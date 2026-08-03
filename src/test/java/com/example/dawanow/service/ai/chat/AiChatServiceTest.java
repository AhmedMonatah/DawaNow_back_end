package com.example.dawanow.service.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.dawanow.ai.PrescriptionAiClient;
import com.example.dawanow.config.AiChatProperties;
import com.example.dawanow.controller.CatalogAiController.CatalogSearchResponse;
import com.example.dawanow.controller.CatalogAiController.ProductMatchResponse;
import com.example.dawanow.dtos.ai.ExtractedPrescription;
import com.example.dawanow.dtos.request.ChatMessageRequest;
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
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {

    @Mock
    private ChatConversationRepository conversationRepository;
    @Mock
    private ChatMessageRepository messageRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductTranslationRepository productTranslationRepository;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private com.example.dawanow.service.ai.rag.CatalogRagService catalogRagService;
    @Mock
    private AiChatModelClient modelClient;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private PrescriptionAiClient prescriptionAiClient;
    @Mock
    private MedicineImageValidator imageValidator;
    @Mock
    private PrescriptionProductMatchingService matchingService;
    @Mock
    private MultipartFile image;

    private AiChatProperties properties;
    private AiChatService service;
    private User customer;

    @BeforeEach
    void setUp() {
        properties = new AiChatProperties();
        service = new AiChatService(
                conversationRepository,
                messageRepository,
                productRepository,
                productTranslationRepository,
                productMapper,
                catalogRagService,
                modelClient,
                new AiChatPromptFactory(),
                new ChatLanguageDetector(),
                new ChatSafetyGuard(),
                currentUserProvider,
                properties,
                prescriptionAiClient,
                imageValidator,
                matchingService
        );

        customer = user(1L, UserRole.CUSTOMER);
    }

    @Test
    void greetingNeverSearchesCatalog() {
        stubCurrentUser(customer);
        stubConversation();
        stubMessageSaves();
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenReturn(new RouterResult(ChatIntent.GREETING, "Hello!", null, List.of(), List.of()));

        ChatMessageResponse response = service.sendMessage(new ChatMessageRequest("hi"));

        assertThat(response.intent()).isEqualTo("GREETING");
        assertThat(response.products()).isEmpty();
        verifyNoInteractions(catalogRagService);
    }

    @Test
    void symptomAdviceUsesSymptomPromptAndLowerProductCap() {
        stubCurrentUser(customer);
        stubConversation();
        stubMessageSaves();
        List<ProductMatchResponse> matches = List.of(
                match(product(1L)), match(product(2L)), match(product(3L)), match(product(4L))
        );
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenReturn(new RouterResult(ChatIntent.SYMPTOM_ADVICE, "", "headache", List.of(), List.of()));
        when(catalogRagService.search(eq("headache"), eq("en"), anyInt()))
                .thenReturn(searchResponse(matches));
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        when(modelClient.generateGrounded(promptCaptor.capture(), any(), anyInt()))
                .thenReturn(new GroundedResult("Rest first, then...", List.of(1L, 2L, 3L, 4L)));

        ChatMessageResponse response = service.sendMessage(
                new ChatMessageRequest("my name is Mahmoud and I have a headache"));

        assertThat(response.intent()).isEqualTo("SYMPTOM_ADVICE");
        // Symptom answers are capped at maxSymptomProducts (2), not maxSuggestedProducts (3).
        assertThat(response.products()).hasSize(2);
        assertThat(promptCaptor.getValue()).contains("Non-medicine steps first");
        assertThat(response.disclaimer()).isNotBlank();
    }

    @Test
    void redFlagMessageNeverReachesTheModelOrCatalog() {
        stubCurrentUser(customer);
        stubConversation();
        stubMessageSaves();

        ChatMessageResponse response = service.sendMessage(
                new ChatMessageRequest("عندي ألم في الصدر وعايز دوا"));

        assertThat(response.intent()).isEqualTo("DOCTOR_SPECIALIZATION");
        assertThat(response.products()).isEmpty();
        assertThat(response.answer()).contains("123");
        verifyNoInteractions(modelClient);
        verifyNoInteractions(catalogRagService);
    }

    @Test
    void redFlagGuardDoesNotApplyToPharmacists() {
        User pharmacist = user(2L, UserRole.PHARMACIST);
        stubCurrentUser(pharmacist);
        when(conversationRepository.findFirstByUserIdOrderByIdAsc(2L)).thenReturn(Optional.empty());
        stubConversationSave();
        stubMessageSaves();
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenReturn(new RouterResult(ChatIntent.DOCTOR_SPECIALIZATION, "Refer to cardiology",
                        null, List.of("Cardiologist"), List.of()));

        ChatMessageResponse response = service.sendMessage(
                new ChatMessageRequest("patient with chest pain, what do you advise"));

        assertThat(response.doctorSpecializations()).containsExactly("Cardiologist");
        verify(modelClient).route(anyString(), any(), anyInt());
    }

    @Test
    void doctorSpecializationNeverAttachesProducts() {
        stubCurrentUser(customer);
        stubConversation();
        stubMessageSaves();
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenReturn(new RouterResult(ChatIntent.DOCTOR_SPECIALIZATION,
                        "This needs a specialist", null, List.of("Gastroenterologist"), List.of()));

        ChatMessageResponse response = service.sendMessage(
                new ChatMessageRequest("my stomach has been hurting for two weeks"));

        assertThat(response.products()).isEmpty();
        assertThat(response.doctorSpecializations()).containsExactly("Gastroenterologist");
        verifyNoInteractions(catalogRagService);
    }

    @Test
    void namedMedicineRequestStillReturnsUpToThreeProducts() {
        stubCurrentUser(customer);
        stubConversation();
        stubMessageSaves();
        List<ProductMatchResponse> matches = List.of(
                match(product(1L)), match(product(2L)), match(product(3L)), match(product(4L))
        );
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenReturn(new RouterResult(ChatIntent.MEDICINE_REQUEST, "", "panadol", List.of(), List.of()));
        when(catalogRagService.search(eq("panadol"), eq("en"), anyInt()))
                .thenReturn(searchResponse(matches));
        when(modelClient.generateGrounded(anyString(), any(), anyInt()))
                .thenReturn(new GroundedResult("Here you go", List.of(1L, 2L, 3L, 4L)));

        ChatMessageResponse response = service.sendMessage(new ChatMessageRequest("do you have panadol?"));

        assertThat(response.products()).hasSize(3);
    }

    @Test
    void modelCitedUnknownProductIdsAreFiltered() {
        stubCurrentUser(customer);
        stubConversation();
        stubMessageSaves();
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenReturn(new RouterResult(ChatIntent.MEDICINE_REQUEST, "", "panadol", List.of(), List.of()));
        when(catalogRagService.search(eq("panadol"), eq("en"), anyInt()))
                .thenReturn(searchResponse(List.of(match(product(1L)), match(product(2L)))));
        when(modelClient.generateGrounded(anyString(), any(), anyInt()))
                .thenReturn(new GroundedResult("Answer", List.of(99L)));

        ChatMessageResponse response = service.sendMessage(new ChatMessageRequest("panadol"));

        assertThat(response.products()).extracting(ProductResponse::id).containsExactly(1L, 2L);
    }

    @Test
    void emergencyReturnsEgyptianNumbers() {
        stubCurrentUser(customer);
        stubConversation();
        stubMessageSaves();
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenReturn(new RouterResult(ChatIntent.EMERGENCY, "Call now",
                        null, List.of(), List.of("ambulance", "FIRE")));

        ChatMessageResponse response = service.sendMessage(new ChatMessageRequest("there is a fire"));

        assertThat(response.emergencyNumbers()).containsExactly(
                new EmergencyNumberResponse("AMBULANCE", "123"),
                new EmergencyNumberResponse("FIRE", "180")
        );
    }

    @Test
    void reusesTheSameConversationForEveryMessage() {
        stubCurrentUser(customer);
        stubMessageSaves();
        ChatConversation existing = conversation(9L, customer);
        when(conversationRepository.findFirstByUserIdOrderByIdAsc(1L)).thenReturn(Optional.of(existing));
        when(conversationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenReturn(new RouterResult(ChatIntent.GREETING, "Hi", null, List.of(), List.of()));

        ChatMessageResponse first = service.sendMessage(new ChatMessageRequest("hello"));
        ChatMessageResponse second = service.sendMessage(new ChatMessageRequest("hello again"));

        assertThat(first.conversationId()).isEqualTo(9L);
        assertThat(second.conversationId()).isEqualTo(9L);
        // No new conversation row is ever created for an existing user.
        verify(conversationRepository, never()).save(org.mockito.ArgumentMatchers
                .argThat(saved -> saved.getId() == null));
    }

    @Test
    void clearHistoryKeepsTheSameConversationId() {
        stubCurrentUser(customer);
        ChatConversation existing = conversation(9L, customer);
        when(conversationRepository.findFirstByUserIdOrderByIdAsc(1L)).thenReturn(Optional.of(existing));
        when(conversationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ChatHistoryResponse response = service.clearHistory();

        assertThat(response.conversationId()).isEqualTo(9L);
        assertThat(response.messages()).isEmpty();
        verify(messageRepository).deleteByConversationId(9L);
    }

    @Test
    void historyIsEmptyBeforeTheFirstMessage() {
        stubCurrentUser(customer);
        when(conversationRepository.findFirstByUserIdOrderByIdAsc(1L)).thenReturn(Optional.empty());

        ChatHistoryResponse response = service.getHistory();

        assertThat(response.conversationId()).isNull();
        assertThat(response.messages()).isEmpty();
    }

    @Test
    void earlierTurnsAreRestatedInlineSoTheModelCannotMissThem() {
        stubCurrentUser(customer);
        stubMessageSaves();
        ChatConversation conversation = conversation(3L, customer);
        when(conversationRepository.findFirstByUserIdOrderByIdAsc(1L)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.findByConversationIdOrderByCreatedAtDescIdDesc(eq(3L), any()))
                .thenReturn(List.of(
                        storedMessage(conversation, ChatMessageRole.ASSISTANT, "older answer"),
                        storedMessage(conversation, ChatMessageRole.USER, "older question")
                ));
        ArgumentCaptor<List<GatewayMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        when(modelClient.route(anyString(), messagesCaptor.capture(), anyInt()))
                .thenReturn(new RouterResult(ChatIntent.GREETING, "Hi", null, List.of(), List.of()));

        service.sendMessage(new ChatMessageRequest("follow up"));

        List<GatewayMessage> sent = messagesCaptor.getValue();
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().content())
                .contains("[Earlier in this chat")
                .contains("user: older question")
                .contains("assistant: older answer")
                .contains("[New message to answer]")
                .endsWith("follow up");
    }

    @Test
    void firstMessageOfAConversationCarriesNoHistoryPreamble() {
        stubCurrentUser(customer);
        stubConversation();
        stubMessageSaves();
        ArgumentCaptor<List<GatewayMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        when(modelClient.route(anyString(), messagesCaptor.capture(), anyInt()))
                .thenReturn(new RouterResult(ChatIntent.GREETING, "Hi", null, List.of(), List.of()));

        service.sendMessage(new ChatMessageRequest("hello"));

        assertThat(messagesCaptor.getValue()).hasSize(1);
        assertThat(messagesCaptor.getValue().getFirst().content()).isEqualTo("hello");
    }

    @Test
    void pharmacistGetsAlternativesBySameScientificName() {
        User pharmacist = user(2L, UserRole.PHARMACIST);
        stubCurrentUser(pharmacist);
        when(conversationRepository.findFirstByUserIdOrderByIdAsc(2L)).thenReturn(Optional.empty());
        stubConversationSave();
        stubMessageSaves();
        ProductResponse panadol = product(1L, "Panadol", "Paracetamol");
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenReturn(new RouterResult(ChatIntent.MEDICINE_REQUEST, "", "panadol", List.of(), List.of()));
        when(catalogRagService.search(eq("panadol"), eq("en"), anyInt()))
                .thenReturn(searchResponse(List.of(match(panadol))));
        when(catalogRagService.search(eq("Paracetamol"), eq("en"), anyInt()))
                .thenReturn(searchResponse(List.of(
                        match(panadol),
                        match(product(2L, "Adol", "Paracetamol")),
                        match(product(3L, "Abimol", "Paracetamol"))
                )));
        when(modelClient.generateGrounded(anyString(), any(), anyInt()))
                .thenReturn(new GroundedResult("Panadol info", List.of(1L)));

        ChatMessageResponse response = service.sendMessage(new ChatMessageRequest("panadol"));

        assertThat(response.alternatives()).extracting(ProductResponse::id).containsExactly(2L, 3L);
    }

    @Test
    void arabicMessageSearchesInArabic() {
        stubCurrentUser(customer);
        stubConversation();
        stubMessageSaves();
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenReturn(new RouterResult(ChatIntent.SYMPTOM_ADVICE, "", "صداع", List.of(), List.of()));
        when(catalogRagService.search(eq("صداع"), eq("ar"), anyInt()))
                .thenReturn(searchResponse(List.of()));

        ChatMessageResponse response = service.sendMessage(new ChatMessageRequest("عندي صداع خفيف"));

        assertThat(response.answer()).contains("لم أجد");
        assertThat(response.products()).isEmpty();
    }

    @Test
    void imageWithNoExtractedMedicinesReturnsRetryReplyWithoutLlmCall() {
        stubCurrentUser(customer);
        stubConversation();
        stubMessageSaves();
        when(imageValidator.read(image, "Chat"))
                .thenReturn(new MedicineImageValidator.ValidatedImage(new byte[]{1}, "image/jpeg"));
        when(prescriptionAiClient.analyze(any(), anyString(), anyString(), any()))
                .thenReturn(new ExtractedPrescription(List.of()));
        when(prescriptionAiClient.analyzeMedicineImage(any(), anyString(), anyString(), any()))
                .thenReturn(Optional.empty());

        ChatMessageResponse response = service.sendImageMessage(null, image, null);

        assertThat(response.answer()).contains("clearer");
        verifyNoInteractions(modelClient);
    }

    private void stubCurrentUser(User user) {
        when(currentUserProvider.get()).thenReturn(user);
    }

    private void stubConversation() {
        when(conversationRepository.findFirstByUserIdOrderByIdAsc(1L)).thenReturn(Optional.empty());
        stubConversationSave();
    }

    private void stubConversationSave() {
        when(conversationRepository.save(any())).thenAnswer(invocation -> {
            ChatConversation conversation = invocation.getArgument(0);
            if (conversation.getId() == null) {
                conversation.setId(1L);
            }
            return conversation;
        });
    }

    private void stubMessageSaves() {
        AtomicLong nextId = new AtomicLong(100);
        when(messageRepository.save(any())).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            if (message.getId() == null) {
                message.setId(nextId.incrementAndGet());
            }
            return message;
        });
    }

    private User user(Long id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private ChatConversation conversation(Long id, User user) {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(id);
        conversation.setUser(user);
        return conversation;
    }

    private ChatMessage storedMessage(ChatConversation conversation, ChatMessageRole role, String content) {
        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private ProductResponse product(Long id) {
        return product(id, "Product " + id, "Ingredient " + id);
    }

    private ProductResponse product(Long id, String name, String scientificName) {
        return new ProductResponse(
                id, name, name, "500mg", "20 tablets", "tablet", new BigDecimal("25.00"),
                scientificName, "Analgesic", 1L, "Pain relief", "Company", "ORAL.SOLID",
                "Relieves pain and fever", "https://example.com/image.png"
        );
    }

    private ProductMatchResponse match(ProductResponse product) {
        return new ProductMatchResponse(product, 0.9, 0.9, 0.9, "hybrid");
    }

    private CatalogSearchResponse searchResponse(List<ProductMatchResponse> matches) {
        return new CatalogSearchResponse("query", "en", true, "cohere", "embed-multilingual-v3.0", matches);
    }
}
