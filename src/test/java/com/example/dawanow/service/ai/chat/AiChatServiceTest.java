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
import com.example.dawanow.dtos.response.PharmacistPerformanceEntryResponse;
import com.example.dawanow.dtos.response.PharmacistRankingResponse;
import com.example.dawanow.entity.ChatConversation;
import com.example.dawanow.entity.ChatIntent;
import com.example.dawanow.entity.ChatMessage;
import com.example.dawanow.entity.ChatMessageRole;
import com.example.dawanow.entity.ChatPerformanceDirection;
import com.example.dawanow.entity.ChatPerformanceMetric;
import com.example.dawanow.entity.DashboardPeriod;
import com.example.dawanow.entity.MedicationReminder;
import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.entity.User;
import com.example.dawanow.entity.UserRole;
import com.example.dawanow.mapper.ProductMapper;
import com.example.dawanow.repo.CategoryRepository;
import com.example.dawanow.repo.CategoryTranslationRepository;
import com.example.dawanow.repo.ChatConversationRepository;
import com.example.dawanow.repo.ChatMessageRepository;
import com.example.dawanow.repo.ProductRepository;
import com.example.dawanow.repo.ProductTranslationRepository;
import com.example.dawanow.repo.PharmacistRepository;
import com.example.dawanow.service.CurrentUserProvider;
import com.example.dawanow.service.MedicineImageValidator;
import com.example.dawanow.service.PrescriptionProductMatchingService;
import com.example.dawanow.service.ai.chat.AiChatModelClient.GatewayMessage;
import com.example.dawanow.service.ai.chat.AiChatModelClient.GroundedResult;
import com.example.dawanow.service.ai.chat.AiChatModelClient.RouterResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

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
    private ChatCartActionService cartActionService;
    @Mock
    private CartAgentService cartAgentService;
    @Mock
    private MedicationReminderService reminderService;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CategoryTranslationRepository categoryTranslationRepository;
    @Mock
    private PharmacistPerformanceService pharmacistPerformanceService;
    @Mock
    private PharmacistRepository pharmacistRepository;
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
                matchingService,
                cartActionService,
                cartAgentService,
                reminderService,
                categoryRepository,
                categoryTranslationRepository,
                pharmacistPerformanceService,
                pharmacistRepository
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
    void pharmacistPerformanceReturnsDeterministicStructuredRanking() {
        User pharmacist = user(2L, UserRole.PHARMACIST);
        stubCurrentUser(pharmacist);
        when(conversationRepository.findFirstByUserIdOrderByIdAsc(2L)).thenReturn(Optional.empty());
        stubConversationSave();
        stubMessageSaves();
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenReturn(new RouterResult(
                        ChatIntent.PHARMACIST_PERFORMANCE,
                        "",
                        null,
                        List.of(),
                        List.of(),
                        null,
                        null,
                        List.of(),
                        ChatPerformanceMetric.OFFERS_CREATED,
                        DashboardPeriod.LAST_MONTH
                ));
        PharmacistRankingResponse ranking = new PharmacistRankingResponse(
                "OFFERS_CREATED",
                "LAST_MONTH",
                "TOP",
                List.of(new PharmacistPerformanceEntryResponse(1, 7L, "Mona", "Ali", 12))
        );
        when(pharmacistPerformanceService.rank(
                pharmacist, ChatPerformanceMetric.OFFERS_CREATED, DashboardPeriod.LAST_MONTH, null))
                .thenReturn(new PharmacistPerformanceService.PerformanceResult(
                        true,
                        ChatPerformanceMetric.OFFERS_CREATED,
                        DashboardPeriod.LAST_MONTH,
                        ChatPerformanceDirection.TOP,
                        10L,
                        List.of(ranking)
                ));

        ChatMessageResponse response = service.sendMessage(
                new ChatMessageRequest("who created the most offers this month?"));

        assertThat(response.intent()).isEqualTo("PHARMACIST_PERFORMANCE");
        assertThat(response.pharmacistRankings()).containsExactly(ranking);
        assertThat(response.answer()).contains("Mona Ali").contains("12");
        verify(messageRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.getRole() == ChatMessageRole.ASSISTANT
                        && saved.getPerformanceMetric() == ChatPerformanceMetric.OFFERS_CREATED
                        && saved.getPerformancePeriod() == DashboardPeriod.LAST_MONTH
                        && saved.getPerformanceDirection() == ChatPerformanceDirection.TOP
                        && Long.valueOf(10L).equals(saved.getPerformancePharmacyId())
                        && "7:12".equals(saved.getOfferRankingEntries())));
        verifyNoInteractions(catalogRagService);
    }

    @Test
    void leastPerformanceRequestReturnsAndPersistsBottomDirection() {
        User pharmacist = user(2L, UserRole.PHARMACIST);
        stubCurrentUser(pharmacist);
        when(conversationRepository.findFirstByUserIdOrderByIdAsc(2L)).thenReturn(Optional.empty());
        stubConversationSave();
        stubMessageSaves();
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenReturn(new RouterResult(
                        ChatIntent.PHARMACIST_PERFORMANCE,
                        "",
                        null,
                        List.of(),
                        List.of(),
                        null,
                        null,
                        List.of(),
                        ChatPerformanceMetric.SUCCESSFUL_ORDERS,
                        DashboardPeriod.LAST_WEEK,
                        ChatPerformanceDirection.BOTTOM
                ));
        PharmacistRankingResponse ranking = new PharmacistRankingResponse(
                "SUCCESSFUL_ORDERS",
                "LAST_WEEK",
                "BOTTOM",
                List.of(new PharmacistPerformanceEntryResponse(1, 8L, "Omar", "Hassan", 0))
        );
        when(pharmacistPerformanceService.rank(
                pharmacist,
                ChatPerformanceMetric.SUCCESSFUL_ORDERS,
                DashboardPeriod.LAST_WEEK,
                ChatPerformanceDirection.BOTTOM
        )).thenReturn(new PharmacistPerformanceService.PerformanceResult(
                true,
                ChatPerformanceMetric.SUCCESSFUL_ORDERS,
                DashboardPeriod.LAST_WEEK,
                ChatPerformanceDirection.BOTTOM,
                10L,
                List.of(ranking)
        ));

        ChatMessageResponse response = service.sendMessage(
                new ChatMessageRequest("who completed the least successful orders last week?"));

        assertThat(response.pharmacistRankings()).containsExactly(ranking);
        assertThat(response.answer()).contains("lowest-performing").contains("Omar Hassan");
        verify(messageRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.getRole() == ChatMessageRole.ASSISTANT
                        && saved.getPerformanceDirection() == ChatPerformanceDirection.BOTTOM
                        && "8:0".equals(saved.getSuccessfulOrderRankingEntries())));
    }

    @Test
    void unauthorizedPerformanceRequestReturnsChatDenialWithoutRankingData() {
        stubCurrentUser(customer);
        stubConversation();
        stubMessageSaves();
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenReturn(new RouterResult(
                        ChatIntent.PHARMACIST_PERFORMANCE,
                        "",
                        null,
                        List.of(),
                        List.of(),
                        null,
                        null,
                        List.of(),
                        ChatPerformanceMetric.BOTH,
                        null
                ));
        when(pharmacistPerformanceService.rank(customer, ChatPerformanceMetric.BOTH, null, null))
                .thenReturn(new PharmacistPerformanceService.PerformanceResult(
                        false,
                        ChatPerformanceMetric.BOTH,
                        DashboardPeriod.LAST_WEEK,
                        ChatPerformanceDirection.TOP,
                        null,
                        List.of()
                ));

        ChatMessageResponse response = service.sendMessage(
                new ChatMessageRequest("show me the best pharmacists"));

        assertThat(response.pharmacistRankings()).isEmpty();
        assertThat(response.answer()).contains("pharmacy admin only");
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
    void historyReconstructsPersistedPharmacistRankings() {
        User pharmacistUser = user(2L, UserRole.PHARMACIST);
        stubCurrentUser(pharmacistUser);
        ChatConversation existing = conversation(9L, pharmacistUser);
        when(conversationRepository.findFirstByUserIdOrderByIdAsc(2L)).thenReturn(Optional.of(existing));

        ChatMessage message = storedMessage(
                existing, ChatMessageRole.ASSISTANT, "Top pharmacy team performance");
        message.setId(50L);
        message.setIntent(ChatIntent.PHARMACIST_PERFORMANCE);
        message.setPerformancePeriod(DashboardPeriod.LAST_WEEK);
        message.setPerformanceMetric(ChatPerformanceMetric.OFFERS_CREATED);
        message.setPerformancePharmacyId(10L);
        message.setOfferRankingEntries("7:12,8:6");
        when(messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(9L))
                .thenReturn(List.of(message));
        when(pharmacistPerformanceService.currentAdminPharmacyId(pharmacistUser)).thenReturn(10L);

        Pharmacist first = pharmacist(7L, "Mona", "Ali");
        Pharmacist second = pharmacist(8L, "Omar", "Hassan");
        when(pharmacistRepository.findAllById(any())).thenReturn(List.of(first, second));

        ChatHistoryResponse response = service.getHistory();

        assertThat(response.messages().getFirst().pharmacistRankings()).hasSize(1);
        assertThat(response.messages().getFirst().pharmacistRankings().getFirst().direction())
                .isEqualTo("TOP");
        assertThat(response.messages().getFirst().pharmacistRankings().getFirst().entries())
                .extracting(PharmacistPerformanceEntryResponse::count)
                .containsExactly(12L, 6L);
    }

    @Test
    void historyHidesPersistedRankingsFromFormerAdmin() {
        User pharmacistUser = user(2L, UserRole.PHARMACIST);
        stubCurrentUser(pharmacistUser);
        ChatConversation existing = conversation(9L, pharmacistUser);
        when(conversationRepository.findFirstByUserIdOrderByIdAsc(2L)).thenReturn(Optional.of(existing));

        ChatMessage message = storedMessage(
                existing, ChatMessageRole.ASSISTANT, "1. **Mona Ali** — **12**");
        message.setIntent(ChatIntent.PHARMACIST_PERFORMANCE);
        message.setPerformancePeriod(DashboardPeriod.LAST_WEEK);
        message.setPerformanceMetric(ChatPerformanceMetric.OFFERS_CREATED);
        message.setPerformancePharmacyId(10L);
        message.setOfferRankingEntries("7:12");
        when(messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(9L))
                .thenReturn(List.of(message));
        when(pharmacistPerformanceService.currentAdminPharmacyId(pharmacistUser)).thenReturn(null);

        ChatHistoryResponse response = service.getHistory();

        assertThat(response.messages().getFirst().pharmacistRankings()).isEmpty();
        assertThat(response.messages().getFirst().content()).contains("pharmacy admin only");
        assertThat(response.messages().getFirst().content()).doesNotContain("Mona", "12");
        verifyNoInteractions(pharmacistRepository);
    }

    @Test
    void historyRestoresBothRankingsIncludingAnEmptyMetric() {
        User pharmacistUser = user(2L, UserRole.PHARMACIST);
        stubCurrentUser(pharmacistUser);
        ChatConversation existing = conversation(9L, pharmacistUser);
        when(conversationRepository.findFirstByUserIdOrderByIdAsc(2L)).thenReturn(Optional.of(existing));

        ChatMessage message = storedMessage(
                existing, ChatMessageRole.ASSISTANT, "Top pharmacy team performance");
        message.setIntent(ChatIntent.PHARMACIST_PERFORMANCE);
        message.setPerformancePeriod(DashboardPeriod.LAST_WEEK);
        message.setPerformanceMetric(ChatPerformanceMetric.BOTH);
        message.setPerformanceDirection(ChatPerformanceDirection.BOTTOM);
        message.setPerformancePharmacyId(10L);
        message.setOfferRankingEntries("7:12");
        when(messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(9L))
                .thenReturn(List.of(message));
        when(pharmacistPerformanceService.currentAdminPharmacyId(pharmacistUser)).thenReturn(10L);
        when(pharmacistRepository.findAllById(any()))
                .thenReturn(List.of(pharmacist(7L, "Mona", "Ali")));

        ChatHistoryResponse response = service.getHistory();

        assertThat(response.messages().getFirst().pharmacistRankings())
                .extracting(PharmacistRankingResponse::metric)
                .containsExactly("OFFERS_CREATED", "SUCCESSFUL_ORDERS");
        assertThat(response.messages().getFirst().pharmacistRankings())
                .extracting(PharmacistRankingResponse::direction)
                .containsOnly("BOTTOM");
        assertThat(response.messages().getFirst().pharmacistRankings().getFirst().entries()).hasSize(1);
        assertThat(response.messages().getFirst().pharmacistRankings().get(1).entries()).isEmpty();
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

    @Test
    void addToCartExecutesAndReturnsAction() {
        stubCurrentUser(customer);
        stubConversation();
        stubMessageSaves();
        ProductResponse panadol = product(7L, "PANADOL ADVANCE", "PARACETAMOL");
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenReturn(new RouterResult(ChatIntent.ADD_TO_CART, "", "panadol",
                        List.of(), List.of(), 2, null));
        stubAgentFallback();
        when(cartActionService.addToCart("panadol", 2, "en"))
                .thenReturn(new ChatCartActionService.CartActionOutcome(
                        ChatCartActionService.Status.ADDED, panadol, List.of(), 2, 3L, List.of()));

        ChatMessageResponse response = service.sendMessage(new ChatMessageRequest("add 2 panadol"));

        assertThat(response.intent()).isEqualTo("ADD_TO_CART");
        assertThat(response.answer()).contains("PANADOL ADVANCE");
        assertThat(response.action().type()).isEqualTo("ADDED_TO_CART");
        assertThat(response.action().addedProductIds()).containsExactly(7L);
        assertThat(response.action().cartItemCount()).isEqualTo(3L);
    }

    @Test
    void addedToCartReplyCarriesInteractionWarning() {
        stubCurrentUser(customer);
        stubConversation();
        stubMessageSaves();
        ProductResponse ibuprofen = product(8L, "BRUFEN", "IBUPROFEN");
        var warning = new com.example.dawanow.dtos.response.InteractionWarningResponse(
                "HIGH", "Blood thinner + NSAID", "Ask your doctor.", List.of());
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenReturn(new RouterResult(ChatIntent.ADD_TO_CART, "", "brufen",
                        List.of(), List.of(), null, null));
        stubAgentFallback();
        when(cartActionService.addToCart("brufen", null, "en"))
                .thenReturn(new ChatCartActionService.CartActionOutcome(
                        ChatCartActionService.Status.ADDED, ibuprofen, List.of(), 1, 2L, List.of(warning)));

        ChatMessageResponse response = service.sendMessage(new ChatMessageRequest("add brufen"));

        assertThat(response.answer()).contains("Drug interaction warning")
                .contains("Blood thinner + NSAID");
    }

    @Test
    void ambiguousAddToCartOffersCandidatesWithoutAction() {
        stubCurrentUser(customer);
        stubConversation();
        stubMessageSaves();
        List<ProductResponse> candidates = List.of(product(1L), product(2L), product(3L));
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenReturn(new RouterResult(ChatIntent.ADD_TO_CART, "", "panadol",
                        List.of(), List.of(), null, null));
        stubAgentFallback();
        when(cartActionService.addToCart("panadol", null, "en"))
                .thenReturn(new ChatCartActionService.CartActionOutcome(
                        ChatCartActionService.Status.AMBIGUOUS, null, candidates, 1, 0, List.of()));

        ChatMessageResponse response = service.sendMessage(new ChatMessageRequest("add panadol"));

        assertThat(response.action()).isNull();
        assertThat(response.products()).hasSize(3);
        assertThat(response.answer()).contains("Product 1").contains("Product 3");
    }

    @Test
    void createRequestReturnsNavigationActionWithoutSideEffects() {
        stubCurrentUser(customer);
        stubConversation();
        stubMessageSaves();
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenReturn(new RouterResult(ChatIntent.CREATE_REQUEST, "", null,
                        List.of(), List.of(), null, null));

        ChatMessageResponse response = service.sendMessage(new ChatMessageRequest("I want to order"));

        assertThat(response.intent()).isEqualTo("CREATE_REQUEST");
        assertThat(response.action().type()).isEqualTo("CREATE_REQUEST");
        verifyNoInteractions(cartActionService);
    }

    @Test
    void pharmacistCannotUseCartActions() {
        stubCurrentUser(user(1L, UserRole.PHARMACIST));
        stubConversation();
        stubMessageSaves();
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenReturn(new RouterResult(ChatIntent.ADD_TO_CART, "", "panadol",
                        List.of(), List.of(), null, null));

        ChatMessageResponse response = service.sendMessage(new ChatMessageRequest("add panadol"));

        assertThat(response.action()).isNull();
        assertThat(response.answer()).contains("customer accounts only");
        verifyNoInteractions(cartActionService);
    }

    @Test
    void setReminderConfirmsExactTimesAndDuration() {
        stubCurrentUser(customer);
        stubConversation();
        stubMessageSaves();
        var spec = new AiChatModelClient.ReminderSpec("Concor", 2, List.of(), null);
        MedicationReminder reminder = new MedicationReminder();
        reminder.setMedicineName("Concor");
        reminder.setTimesCsv("09:00,21:00");
        reminder.setDurationDays(7);
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenReturn(new RouterResult(ChatIntent.SET_REMINDER, "", null,
                        List.of(), List.of(), null, spec));
        when(reminderService.create(customer, spec)).thenReturn(reminder);
        when(reminderService.times(reminder)).thenReturn(List.of("09:00", "21:00"));

        ChatMessageResponse response = service.sendMessage(
                new ChatMessageRequest("remind me to take concor twice a day"));

        assertThat(response.intent()).isEqualTo("SET_REMINDER");
        assertThat(response.answer()).contains("Concor").contains("09:00").contains("7");
    }

    @Test
    void setReminderWithoutMedicineAsksForIt() {
        stubCurrentUser(customer);
        stubConversation();
        stubMessageSaves();
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenReturn(new RouterResult(ChatIntent.SET_REMINDER, "", null,
                        List.of(), List.of(), null, null));

        ChatMessageResponse response = service.sendMessage(new ChatMessageRequest("remind me"));

        assertThat(response.answer()).contains("Which medicine");
        verify(reminderService, never()).create(any(), any());
    }

    @Test
    void deleteReminderReportsWhatWasCancelled() {
        stubCurrentUser(customer);
        stubConversation();
        stubMessageSaves();
        var spec = new AiChatModelClient.ReminderSpec("concor", null, List.of(), null);
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenReturn(new RouterResult(ChatIntent.DELETE_REMINDER, "", null,
                        List.of(), List.of(), null, spec));
        when(reminderService.deactivateByName(customer, "concor"))
                .thenReturn(new MedicationReminderService.DeletionResult(
                        MedicationReminderService.DeletionStatus.DELETED, List.of("Concor")));

        ChatMessageResponse response = service.sendMessage(new ChatMessageRequest("stop the concor reminder"));

        assertThat(response.intent()).isEqualTo("DELETE_REMINDER");
        assertThat(response.answer()).contains("Concor");
    }

    @Test
    void categoryBrowseResolvesRealCategoriesAndDropsInventedOnes() {
        stubCurrentUser(customer);
        stubConversation();
        stubMessageSaves();
        com.example.dawanow.entity.Category skinCare = new com.example.dawanow.entity.Category();
        skinCare.setId(5L);
        skinCare.setName("SKIN CARE");
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenReturn(new RouterResult(ChatIntent.CATEGORY_BROWSE, "Have a look:", null,
                        List.of(), List.of(), null, null, List.of("SKIN CARE", "IMAGINARY")));
        when(categoryRepository.findByNameIgnoreCase("SKIN CARE"))
                .thenReturn(Optional.of(skinCare));
        when(categoryRepository.findByNameIgnoreCase("IMAGINARY")).thenReturn(Optional.empty());

        ChatMessageResponse response = service.sendMessage(
                new ChatMessageRequest("what sections do you have?"));

        assertThat(response.intent()).isEqualTo("CATEGORY_BROWSE");
        assertThat(response.categories()).hasSize(1);
        assertThat(response.categories().getFirst().id()).isEqualTo(5L);
        assertThat(response.categories().getFirst().name()).isEqualTo("SKIN CARE");
    }

    @Test
    void categoryBrowseWithNoRealMatchesDegradesToPlainReply() {
        stubCurrentUser(customer);
        stubConversation();
        stubMessageSaves();
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenReturn(new RouterResult(ChatIntent.CATEGORY_BROWSE, "Here you go", null,
                        List.of(), List.of(), null, null, List.of("IMAGINARY")));
        when(categoryRepository.findByNameIgnoreCase("IMAGINARY")).thenReturn(Optional.empty());

        ChatMessageResponse response = service.sendMessage(new ChatMessageRequest("sections?"));

        assertThat(response.intent()).isEqualTo("OTHER");
        assertThat(response.categories()).isEmpty();
        assertThat(response.answer()).isEqualTo("Here you go");
    }

    @Test
    void failedTurnDeletesTheUserMessageSoRetriesCannotDuplicateIt() {
        stubCurrentUser(customer);
        stubConversation();
        stubMessageSaves();
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "gateway down"));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.sendMessage(new ChatMessageRequest("hello")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);

        ArgumentCaptor<ChatMessage> deleted = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository).delete(deleted.capture());
        assertThat(deleted.getValue().getContent()).isEqualTo("hello");
        assertThat(deleted.getValue().getRole()).isEqualTo(ChatMessageRole.USER);
    }

    @Test
    void agentAddedOutcomeSkipsTheDeterministicPath() {
        stubCurrentUser(customer);
        stubConversation();
        stubMessageSaves();
        ProductResponse panadol = product(7L, "PANADOL ADVANCE", "PARACETAMOL");
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenReturn(new RouterResult(ChatIntent.ADD_TO_CART, "", "panadol",
                        List.of(), List.of(), 2, null));
        when(cartAgentService.run("add 2 panadol", 2, "en"))
                .thenReturn(new CartAgentService.AgentOutcome(
                        CartAgentService.Status.ADDED, panadol, 2, 3L, List.of(), null,
                        List.of(), 3));

        ChatMessageResponse response = service.sendMessage(new ChatMessageRequest("add 2 panadol"));

        assertThat(response.action().type()).isEqualTo("ADDED_TO_CART");
        assertThat(response.action().addedProductIds()).containsExactly(7L);
        verifyNoInteractions(cartActionService);
    }

    @Test
    void agentAskUserOutcomeReturnsItsQuestionAndCandidates() {
        stubCurrentUser(customer);
        stubConversation();
        stubMessageSaves();
        when(modelClient.route(anyString(), any(), anyInt()))
                .thenReturn(new RouterResult(ChatIntent.ADD_TO_CART, "", "panadol",
                        List.of(), List.of(), null, null));
        when(cartAgentService.run("add panadol", null, "en"))
                .thenReturn(new CartAgentService.AgentOutcome(
                        CartAgentService.Status.ASK_USER, null, 0, 0, List.of(),
                        "Which one do you mean?", List.of(product(1L), product(2L)), 2));

        ChatMessageResponse response = service.sendMessage(new ChatMessageRequest("add panadol"));

        assertThat(response.answer()).isEqualTo("Which one do you mean?");
        assertThat(response.products()).hasSize(2);
        assertThat(response.action()).isNull();
        verifyNoInteractions(cartActionService);
    }

    private void stubAgentFallback() {
        when(cartAgentService.run(anyString(), any(), anyString()))
                .thenReturn(CartAgentService.AgentOutcome.fallback());
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

    private Pharmacist pharmacist(Long id, String firstName, String lastName) {
        Pharmacist pharmacist = new Pharmacist();
        pharmacist.setId(id);
        pharmacist.setFirstName(firstName);
        pharmacist.setLastName(lastName);
        return pharmacist;
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
