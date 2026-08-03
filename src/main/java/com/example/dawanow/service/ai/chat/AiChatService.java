package com.example.dawanow.service.ai.chat;

import com.example.dawanow.ai.PrescriptionAiClient;
import com.example.dawanow.config.AiChatProperties;
import com.example.dawanow.controller.CatalogAiController.ProductMatchResponse;
import com.example.dawanow.dtos.ai.ExtractedMedicine;
import com.example.dawanow.dtos.ai.ExtractedPrescription;
import com.example.dawanow.dtos.request.ChatMessageRequest;
import com.example.dawanow.dtos.response.ChatHistoryMessageResponse;
import com.example.dawanow.dtos.response.ChatHistoryResponse;
import com.example.dawanow.dtos.response.ChatMessageResponse;
import com.example.dawanow.dtos.response.EmergencyNumberResponse;
import com.example.dawanow.dtos.response.ProductResponse;
import com.example.dawanow.entity.ChatConversation;
import com.example.dawanow.entity.ChatIntent;
import com.example.dawanow.entity.ChatMessage;
import com.example.dawanow.entity.ChatMessageRole;
import com.example.dawanow.entity.User;
import com.example.dawanow.entity.UserRole;
import com.example.dawanow.mapper.ProductMapper;
import com.example.dawanow.repo.ChatConversationRepository;
import com.example.dawanow.repo.ChatMessageRepository;
import com.example.dawanow.repo.ProductRepository;
import com.example.dawanow.repo.ProductTranslationRepository;
import com.example.dawanow.service.CurrentUserProvider;
import com.example.dawanow.service.MedicineImageValidator;
import com.example.dawanow.service.PrescriptionProductMatchingService;
import com.example.dawanow.service.ai.chat.AiChatModelClient.GatewayMessage;
import com.example.dawanow.service.ai.chat.AiChatModelClient.GroundedResult;
import com.example.dawanow.service.ai.chat.AiChatModelClient.RouterResult;
import com.example.dawanow.service.ai.rag.CatalogRagService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Conversational AI assistant grounded in the Medsy product catalog.
 *
 * <p>Every user owns exactly one conversation, resolved automatically from the
 * authenticated caller, so clients never pass a conversation id. Starting a new
 * chat clears the messages while keeping the same conversation.</p>
 *
 * <p>Send methods are intentionally not transactional: the AI gateway calls can
 * take up to the configured request timeout, and holding a database connection
 * across them would starve the connection pool. Each repository save commits on
 * its own, so the user's message is preserved even when generation fails.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiChatService {

    private static final int MAX_SEARCH_QUERY_LENGTH = 500;
    private static final int TITLE_MAX_LENGTH = 80;
    /** Keeps replayed turns short so the new message stays the focus. */
    private static final int HISTORY_ENTRY_MAX_CHARS = 300;
    private static final String ARABIC = "ar";
    private static final String IMAGE_MARKER = "[image]";
    private static final Map<String, String> EMERGENCY_NUMBERS = Map.of(
            "AMBULANCE", "123",
            "POLICE", "122",
            "FIRE", "180"
    );
    /** Intents where the user asked about a named medicine, so a catalog lookup is appropriate. */
    private static final Set<ChatIntent> LOOKUP_INTENTS =
            Set.of(ChatIntent.MEDICINE_REQUEST, ChatIntent.MEDICINE_USAGE);

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final ProductRepository productRepository;
    private final ProductTranslationRepository productTranslationRepository;
    private final ProductMapper productMapper;
    private final CatalogRagService catalogRagService;
    private final AiChatModelClient modelClient;
    private final AiChatPromptFactory promptFactory;
    private final ChatLanguageDetector languageDetector;
    private final ChatSafetyGuard safetyGuard;
    private final CurrentUserProvider currentUserProvider;
    private final AiChatProperties properties;
    private final PrescriptionAiClient prescriptionAiClient;
    private final MedicineImageValidator imageValidator;
    private final PrescriptionProductMatchingService matchingService;

    public ChatMessageResponse sendMessage(ChatMessageRequest request) {
        User user = currentUserProvider.get();
        ChatConversation conversation = currentConversation(user, request.message());
        List<GatewayMessage> history = loadHistory(conversation.getId());
        saveUserMessage(conversation, request.message());

        String language = languageDetector.detect(request.message());
        List<GatewayMessage> messages = withCurrentTurn(history, request.message());

        // A red flag in the patient's own words overrides the model entirely.
        if (user.getRole() != UserRole.PHARMACIST && safetyGuard.hasRedFlag(request.message())) {
            log.info("Chat safety guard suppressed medicine suggestions for user {}", user.getId());
            return respondWithRedFlag(conversation, language);
        }

        RouterResult router = modelClient.route(
                promptFactory.routerSystemPrompt(user.getRole()),
                messages,
                properties.getMaxTokens()
        );
        ChatIntent intent = router.intent() == null ? ChatIntent.OTHER : router.intent();

        if (LOOKUP_INTENTS.contains(intent)) {
            return respondFromCatalog(conversation, user, messages, language, intent,
                    router.searchQuery(), request.message(), false);
        }
        if (intent == ChatIntent.SYMPTOM_ADVICE) {
            return respondFromCatalog(conversation, user, messages, language, intent,
                    router.searchQuery(), request.message(), true);
        }
        return respondDirectly(conversation, language, intent, router);
    }

    public ChatMessageResponse sendImageMessage(String caption, MultipartFile image, String aiApiKey) {
        User user = currentUserProvider.get();
        MedicineImageValidator.ValidatedImage validatedImage = imageValidator.read(image, "Chat");
        ChatConversation conversation = currentConversation(
                user,
                StringUtils.hasText(caption) ? caption : "Medicine photo"
        );
        List<GatewayMessage> history = loadHistory(conversation.getId());
        String language = languageDetector.detect(caption);

        List<ExtractedMedicine> medicines = extractMedicines(validatedImage, language, aiApiKey);
        String userContent = imageMessageContent(caption, medicines);
        saveUserMessage(conversation, userContent);

        if (medicines.isEmpty()) {
            String reply = promptFactory.unreadableImageReply(language);
            ChatMessage saved = saveAssistantMessage(conversation, reply, ChatIntent.OTHER,
                    List.of(), List.of(), List.of(), List.of());
            return response(conversation, saved, reply, List.of(), List.of(), List.of(), List.of(), null);
        }

        List<ProductResponse> products = matchProducts(medicines, language);
        if (products.isEmpty()) {
            String reply = promptFactory.notFoundReply(language);
            ChatMessage saved = saveAssistantMessage(conversation, reply, ChatIntent.MEDICINE_REQUEST,
                    List.of(), List.of(), List.of(), List.of());
            return response(conversation, saved, reply, List.of(), List.of(), List.of(), List.of(), null);
        }

        String question = "The user sent a photo of a prescription or medicine box. Extracted medicine names: "
                + medicines.stream().map(ExtractedMedicine::name).collect(Collectors.joining(", "))
                + ". Respond conversationally, briefly introduce each matched product and what it is used for."
                + (StringUtils.hasText(caption) ? "\nUser caption:\n" + caption : "");
        GroundedResult grounded = modelClient.generateGrounded(
                promptFactory.lookupSystemPrompt(user.getRole(), properties.getMaxSuggestedProducts()),
                withCurrentTurn(history, question + "\n\nRetrieved product context:\n" + buildContext(products)),
                properties.getMaxTokens()
        );

        List<ProductResponse> cited = filterCited(products, grounded.productIds(),
                properties.getMaxSuggestedProducts());
        String reply = StringUtils.hasText(grounded.reply())
                ? grounded.reply()
                : promptFactory.fallbackReply(language);
        String disclaimer = promptFactory.disclaimer(language);
        ChatMessage saved = saveAssistantMessage(conversation, reply, ChatIntent.MEDICINE_REQUEST,
                cited, List.of(), List.of(), List.of());
        return response(conversation, saved, reply, cited, List.of(), List.of(), List.of(), disclaimer);
    }

    @Transactional(readOnly = true)
    public ChatHistoryResponse getHistory() {
        User user = currentUserProvider.get();
        ChatConversation conversation = conversationRepository
                .findFirstByUserIdOrderByIdAsc(user.getId())
                .orElse(null);
        if (conversation == null) {
            return new ChatHistoryResponse(null, List.of());
        }

        List<ChatMessage> messages =
                messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversation.getId());
        Map<Long, ProductResponse> english = resolveEnglishProducts(messages);
        return new ChatHistoryResponse(
                conversation.getId(),
                messages.stream().map(message -> toHistoryResponse(message, english)).toList()
        );
    }

    /**
     * "New chat": wipes every message but keeps the same conversation row, so the
     * caller keeps a stable conversation id.
     */
    @Transactional
    public ChatHistoryResponse clearHistory() {
        User user = currentUserProvider.get();
        ChatConversation conversation = conversationRepository
                .findFirstByUserIdOrderByIdAsc(user.getId())
                .orElse(null);
        if (conversation == null) {
            return new ChatHistoryResponse(null, List.of());
        }

        messageRepository.deleteByConversationId(conversation.getId());
        conversation.setTitle(null);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        return new ChatHistoryResponse(conversation.getId(), List.of());
    }

    private ChatMessageResponse respondFromCatalog(
            ChatConversation conversation,
            User user,
            List<GatewayMessage> messages,
            String language,
            ChatIntent intent,
            String searchQuery,
            String rawMessage,
            boolean symptomDriven
    ) {
        int maxProducts = symptomDriven
                ? properties.getMaxSymptomProducts()
                : properties.getMaxSuggestedProducts();
        String query = StringUtils.hasText(searchQuery) ? searchQuery.trim() : rawMessage.trim();
        List<ProductMatchResponse> matches = searchCatalog(query, language);

        if (matches.isEmpty()) {
            String reply = promptFactory.notFoundReply(language);
            ChatMessage saved = saveAssistantMessage(conversation, reply, intent,
                    List.of(), List.of(), List.of(), List.of());
            return response(conversation, saved, reply, List.of(), List.of(), List.of(), List.of(), null);
        }

        List<ProductResponse> retrieved = matches.stream().map(ProductMatchResponse::product).toList();
        String systemPrompt = symptomDriven
                ? promptFactory.symptomSystemPrompt(user.getRole(), maxProducts)
                : promptFactory.lookupSystemPrompt(user.getRole(), maxProducts);
        GroundedResult grounded = modelClient.generateGrounded(
                systemPrompt,
                appendContext(messages, retrieved),
                properties.getMaxTokens()
        );

        List<ProductResponse> products = filterCited(retrieved, grounded.productIds(), maxProducts);
        List<ProductResponse> alternatives = findAlternatives(user, intent, products, language);
        String reply = StringUtils.hasText(grounded.reply())
                ? grounded.reply()
                : promptFactory.fallbackReply(language);
        String disclaimer = promptFactory.disclaimer(language);
        ChatMessage saved = saveAssistantMessage(conversation, reply, intent,
                products, alternatives, List.of(), List.of());
        return response(conversation, saved, reply, products, alternatives, List.of(), List.of(), disclaimer);
    }

    private ChatMessageResponse respondDirectly(
            ChatConversation conversation,
            String language,
            ChatIntent intent,
            RouterResult router
    ) {
        String reply = StringUtils.hasText(router.reply())
                ? router.reply()
                : promptFactory.fallbackReply(language);
        List<String> specializations = intent == ChatIntent.DOCTOR_SPECIALIZATION
                ? router.doctorSpecializations()
                : List.of();
        List<String> emergencyServices = intent == ChatIntent.EMERGENCY
                ? normalizeEmergencyServices(router.emergencyServices())
                : List.of();

        ChatMessage saved = saveAssistantMessage(conversation, reply, intent,
                List.of(), List.of(), specializations, emergencyServices);
        return response(conversation, saved, reply, List.of(), List.of(), specializations,
                emergencyNumbers(emergencyServices), null);
    }

    private ChatMessageResponse respondWithRedFlag(ChatConversation conversation, String language) {
        String reply = promptFactory.redFlagReply(language);
        ChatMessage saved = saveAssistantMessage(conversation, reply, ChatIntent.DOCTOR_SPECIALIZATION,
                List.of(), List.of(), List.of(), List.of());
        return response(conversation, saved, reply, List.of(), List.of(), List.of(), List.of(), null);
    }

    private List<ProductMatchResponse> searchCatalog(String query, String language) {
        String bounded = query.length() > MAX_SEARCH_QUERY_LENGTH
                ? query.substring(0, MAX_SEARCH_QUERY_LENGTH)
                : query;
        try {
            return catalogRagService.search(bounded, language, properties.getRetrievalTopK()).matches();
        } catch (IllegalArgumentException exception) {
            log.warn("Chat catalog search skipped: {}", exception.getMessage());
            return List.of();
        }
    }

    private List<ProductResponse> filterCited(
            List<ProductResponse> retrieved,
            List<Long> citedIds,
            int maxProducts
    ) {
        Set<Long> allowed = retrieved.stream().map(ProductResponse::id).collect(Collectors.toSet());
        List<ProductResponse> cited = retrieved.stream()
                .filter(product -> citedIds.contains(product.id()) && allowed.contains(product.id()))
                .toList();
        List<ProductResponse> selected = cited.isEmpty() ? retrieved : cited;
        return selected.stream().limit(maxProducts).toList();
    }

    private List<ProductResponse> findAlternatives(
            User user,
            ChatIntent intent,
            List<ProductResponse> products,
            String language
    ) {
        if (user.getRole() != UserRole.PHARMACIST
                || intent != ChatIntent.MEDICINE_REQUEST
                || products.isEmpty()) {
            return List.of();
        }
        String scientificName = products.getFirst().scientificName();
        if (!StringUtils.hasText(scientificName)) {
            return List.of();
        }

        Set<Long> suggestedIds = products.stream().map(ProductResponse::id).collect(Collectors.toSet());
        return searchCatalog(scientificName.trim(), language).stream()
                .map(ProductMatchResponse::product)
                .filter(product -> !suggestedIds.contains(product.id()))
                .limit(properties.getMaxSuggestedProducts())
                .toList();
    }

    private List<ExtractedMedicine> extractMedicines(
            MedicineImageValidator.ValidatedImage image,
            String language,
            String aiApiKey
    ) {
        ExtractedPrescription extracted = prescriptionAiClient.analyze(
                image.bytes(), image.contentType(), language, aiApiKey);
        List<ExtractedMedicine> medicines = extracted == null || extracted.medicines() == null
                ? List.of()
                : extracted.medicines().stream()
                        .filter(medicine -> medicine != null && StringUtils.hasText(medicine.name()))
                        .toList();
        if (!medicines.isEmpty()) {
            return medicines;
        }
        return prescriptionAiClient.analyzeMedicineImage(image.bytes(), image.contentType(), language, aiApiKey)
                .filter(medicine -> StringUtils.hasText(medicine.name()))
                .map(List::of)
                .orElseGet(List::of);
    }

    private List<ProductResponse> matchProducts(List<ExtractedMedicine> medicines, String language) {
        Map<Long, ProductResponse> distinct = new LinkedHashMap<>();
        for (ExtractedMedicine medicine : medicines) {
            if (distinct.size() >= properties.getMaxSuggestedProducts()) {
                break;
            }
            matchingService.findTopProductsByMedicineName(medicine.name(), language).stream()
                    .findFirst()
                    .ifPresent(product -> distinct.putIfAbsent(product.id(), product));
        }
        return List.copyOf(distinct.values());
    }

    private ChatConversation currentConversation(User user, String titleSource) {
        return conversationRepository.findFirstByUserIdOrderByIdAsc(user.getId())
                .orElseGet(() -> {
                    ChatConversation conversation = new ChatConversation();
                    conversation.setUser(user);
                    conversation.setTitle(truncate(titleSource));
                    return conversationRepository.save(conversation);
                });
    }

    private List<GatewayMessage> loadHistory(Long conversationId) {
        List<ChatMessage> window = messageRepository.findByConversationIdOrderByCreatedAtDescIdDesc(
                conversationId,
                PageRequest.of(0, properties.getHistoryWindow())
        );
        List<GatewayMessage> history = new ArrayList<>(window.size());
        for (int index = window.size() - 1; index >= 0; index--) {
            ChatMessage message = window.get(index);
            history.add(new GatewayMessage(
                    message.getRole() == ChatMessageRole.ASSISTANT ? "assistant" : "user",
                    message.getContent()
            ));
        }
        log.info("Chat conversation {} replaying {} earlier message(s)", conversationId, history.size());
        return history;
    }

    /**
     * Flattens the conversation into a single user turn.
     *
     * <p>The gateway accepts a multi-turn array, but the small generation model
     * reliably ignores earlier entries once the system prompt carries all of the
     * safety and routing rules, which made follow-ups like "what did I tell you?"
     * fail. Restating the history inline, immediately before the new message,
     * keeps it in the model's field of view.</p>
     */
    private List<GatewayMessage> withCurrentTurn(List<GatewayMessage> history, String content) {
        if (history.isEmpty()) {
            return List.of(new GatewayMessage("user", content));
        }

        StringBuilder turn = new StringBuilder("[Earlier in this chat, oldest first]\n");
        for (GatewayMessage message : history) {
            turn.append(message.role()).append(": ")
                    .append(abbreviate(message.content()))
                    .append('\n');
        }
        turn.append("\n[New message to answer]\n").append(content);
        return List.of(new GatewayMessage("user", turn.toString()));
    }

    private String abbreviate(String content) {
        String single = content.replaceAll("\\s+", " ").trim();
        return single.length() <= HISTORY_ENTRY_MAX_CHARS
                ? single
                : single.substring(0, HISTORY_ENTRY_MAX_CHARS) + "…";
    }

    private List<GatewayMessage> appendContext(List<GatewayMessage> messages, List<ProductResponse> products) {
        List<GatewayMessage> withContext = new ArrayList<>(messages);
        GatewayMessage last = withContext.removeLast();
        withContext.add(new GatewayMessage(
                "user",
                last.content() + "\n\nRetrieved product context:\n" + buildContext(products)
        ));
        return withContext;
    }

    private String buildContext(List<ProductResponse> products) {
        StringBuilder context = new StringBuilder();
        for (ProductResponse product : products) {
            context.append("PRODUCT\n")
                    .append("id: ").append(product.id()).append('\n')
                    .append("name: ").append(value(product.name())).append('\n')
                    .append("productName: ").append(value(product.productName())).append('\n')
                    .append("strength: ").append(value(product.strength())).append('\n')
                    .append("packSize: ").append(value(product.packSize())).append('\n')
                    .append("form: ").append(value(product.form())).append('\n')
                    .append("scientificName: ").append(value(product.scientificName())).append('\n')
                    .append("scientificCategory: ").append(value(product.scientificCategory())).append('\n')
                    .append("consumerCategory: ").append(value(product.consumerCategory())).append('\n')
                    .append("company: ").append(value(product.company())).append('\n')
                    .append("route: ").append(value(product.route())).append('\n')
                    .append("description: ").append(value(product.description())).append('\n')
                    .append("listedPriceEGP: ").append(product.price()).append("\n\n");
        }
        return context.toString();
    }

    private void saveUserMessage(ChatConversation conversation, String content) {
        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setRole(ChatMessageRole.USER);
        message.setContent(content);
        messageRepository.save(message);
        touch(conversation, content);
    }

    private ChatMessage saveAssistantMessage(
            ChatConversation conversation,
            String content,
            ChatIntent intent,
            List<ProductResponse> products,
            List<ProductResponse> alternatives,
            List<String> doctorSpecializations,
            List<String> emergencyServices
    ) {
        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setRole(ChatMessageRole.ASSISTANT);
        message.setContent(content);
        message.setIntent(intent);
        message.setProductIds(joinIds(products));
        message.setAlternativeProductIds(joinIds(alternatives));
        message.setDoctorSpecializations(joinValues(doctorSpecializations));
        message.setEmergencyServices(joinValues(emergencyServices));
        return messageRepository.save(message);
    }

    private void touch(ChatConversation conversation, String content) {
        if (!StringUtils.hasText(conversation.getTitle())) {
            conversation.setTitle(truncate(content));
        }
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    private ChatMessageResponse response(
            ChatConversation conversation,
            ChatMessage message,
            String reply,
            List<ProductResponse> products,
            List<ProductResponse> alternatives,
            List<String> doctorSpecializations,
            List<EmergencyNumberResponse> emergencyNumbers,
            String disclaimer
    ) {
        return new ChatMessageResponse(
                conversation.getId(),
                message.getId(),
                message.getIntent() == null ? ChatIntent.OTHER.name() : message.getIntent().name(),
                reply,
                products,
                alternatives,
                doctorSpecializations,
                emergencyNumbers,
                disclaimer
        );
    }

    private ChatHistoryMessageResponse toHistoryResponse(ChatMessage message, Map<Long, ProductResponse> english) {
        String language = languageDetector.detect(message.getContent());
        return new ChatHistoryMessageResponse(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                message.getIntent() == null ? null : message.getIntent().name(),
                resolveProducts(message.getProductIds(), language, english),
                resolveProducts(message.getAlternativeProductIds(), language, english),
                splitValues(message.getDoctorSpecializations()),
                emergencyNumbers(splitValues(message.getEmergencyServices())),
                message.getCreatedAt()
        );
    }

    private Map<Long, ProductResponse> resolveEnglishProducts(List<ChatMessage> messages) {
        Set<Long> ids = new LinkedHashSet<>();
        for (ChatMessage message : messages) {
            ids.addAll(splitIds(message.getProductIds()));
            ids.addAll(splitIds(message.getAlternativeProductIds()));
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return productRepository.findAllById(ids).stream()
                .map(productMapper::toResponse)
                .collect(Collectors.toMap(ProductResponse::id, Function.identity(), (left, right) -> left,
                        HashMap::new));
    }

    private List<ProductResponse> resolveProducts(
            String idsCsv,
            String language,
            Map<Long, ProductResponse> english
    ) {
        List<Long> ids = splitIds(idsCsv);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<ProductResponse> products = new ArrayList<>(ids.size());
        for (Long id : ids) {
            ProductResponse product = english.get(id);
            if (product == null) {
                continue;
            }
            if (ARABIC.equals(language)) {
                product = productTranslationRepository.findByProductIdAndLang(id, ARABIC)
                        .map(productMapper::toResponse)
                        .orElse(product);
            }
            products.add(product);
        }
        return products;
    }

    private List<String> normalizeEmergencyServices(List<String> services) {
        List<String> normalized = services.stream()
                .map(service -> service.trim().toUpperCase(Locale.ROOT))
                .filter(EMERGENCY_NUMBERS::containsKey)
                .distinct()
                .toList();
        return normalized.isEmpty() ? List.of("AMBULANCE") : normalized;
    }

    private List<EmergencyNumberResponse> emergencyNumbers(List<String> services) {
        return services.stream()
                .filter(EMERGENCY_NUMBERS::containsKey)
                .map(service -> new EmergencyNumberResponse(service, EMERGENCY_NUMBERS.get(service)))
                .toList();
    }

    private String imageMessageContent(String caption, List<ExtractedMedicine> medicines) {
        StringBuilder content = new StringBuilder();
        if (StringUtils.hasText(caption)) {
            content.append(caption.trim()).append('\n');
        }
        content.append(IMAGE_MARKER);
        if (!medicines.isEmpty()) {
            content.append(" Extracted: ").append(medicines.stream()
                    .map(ExtractedMedicine::name)
                    .collect(Collectors.joining(", ")));
        }
        return content.toString();
    }

    private String joinIds(List<ProductResponse> products) {
        if (products == null || products.isEmpty()) {
            return null;
        }
        return products.stream()
                .map(product -> String.valueOf(product.id()))
                .collect(Collectors.joining(","));
    }

    private String joinValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join(",", values);
    }

    private List<Long> splitIds(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String value : csv.split(",")) {
            try {
                ids.add(Long.parseLong(value.trim()));
            } catch (NumberFormatException ignored) {
                // Skip malformed entries rather than failing the whole history.
            }
        }
        return ids;
    }

    private List<String> splitValues(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String truncate(String value) {
        if (!StringUtils.hasText(value)) {
            return "New conversation";
        }
        String trimmed = value.trim();
        return trimmed.length() <= TITLE_MAX_LENGTH ? trimmed : trimmed.substring(0, TITLE_MAX_LENGTH);
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
