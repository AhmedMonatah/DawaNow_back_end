package com.example.dawanow.service.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.dawanow.config.AiChatProperties;
import com.example.dawanow.controller.CatalogAiController.CatalogSearchResponse;
import com.example.dawanow.controller.CatalogAiController.ProductMatchResponse;
import com.example.dawanow.dtos.request.AddCartItemRequest;
import com.example.dawanow.dtos.response.CartResponse;
import com.example.dawanow.dtos.response.InteractionWarningResponse;
import com.example.dawanow.dtos.response.ProductResponse;
import com.example.dawanow.service.CartService;
import com.example.dawanow.service.ai.chat.AiChatModelClient.AgentStep;
import com.example.dawanow.service.ai.chat.CartAgentService.AgentOutcome;
import com.example.dawanow.service.ai.chat.CartAgentService.Status;
import com.example.dawanow.service.ai.interactions.CartInteractionService;
import com.example.dawanow.service.ai.rag.CatalogRagService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CartAgentServiceTest {

    @Mock
    private CatalogRagService catalogRagService;
    @Mock
    private CartService cartService;
    @Mock
    private CartInteractionService cartInteractionService;
    @Mock
    private AiChatModelClient modelClient;

    private CartAgentService service;

    @BeforeEach
    void setUp() {
        service = new CartAgentService(
                catalogRagService, cartService, cartInteractionService,
                modelClient, new AiChatPromptFactory(), new AiChatProperties());
    }

    @Test
    void fullLoopSearchesChecksInteractionsThenAdds() {
        ProductResponse panadol = product(7L, "PANADOL ADVANCE");
        stubSearch("panadol", match(panadol, 0.93));
        when(cartInteractionService.previewWarningsForCandidate(7L, "en")).thenReturn(List.of());
        when(cartService.addItem(new AddCartItemRequest(7L, 2L), "en"))
                .thenReturn(new CartResponse(1L, List.of(), BigDecimal.TEN));
        when(modelClient.agentStep(anyString(), any(), anyInt())).thenReturn(
                step("search_catalog", "panadol", null, null),
                step("check_interactions", null, 7L, null),
                step("add_to_cart", null, 7L, 2)
        );

        AgentOutcome outcome = service.run("add 2 panadol please", 2, "en");

        assertThat(outcome.status()).isEqualTo(Status.ADDED);
        assertThat(outcome.product().id()).isEqualTo(7L);
        assertThat(outcome.quantity()).isEqualTo(2);
        assertThat(outcome.stepsUsed()).isEqualTo(3);
        verify(cartService).addItem(new AddCartItemRequest(7L, 2L), "en");
    }

    @Test
    void observationsFromEarlierStepsReachTheNextDecision() {
        ProductResponse panadol = product(7L, "PANADOL ADVANCE");
        stubSearch("panadol", match(panadol, 0.93));
        ArgumentCaptor<List<AiChatModelClient.GatewayMessage>> turns =
                ArgumentCaptor.forClass(List.class);
        when(modelClient.agentStep(anyString(), turns.capture(), anyInt())).thenReturn(
                step("search_catalog", "panadol", null, null),
                step("ask_user", null, null, null, "Which strength do you need?", null)
        );

        service.run("panadol", null, "en");

        // The second decision must see the first step's observation inline.
        String secondTurn = turns.getAllValues().get(1).getFirst().content();
        assertThat(secondTurn).contains("search_catalog(\"panadol\")")
                .contains("id=7")
                .contains("PANADOL ADVANCE");
    }

    @Test
    void highSeverityInteractionBlocksTheAddInJavaNotByModelChoice() {
        ProductResponse brufen = product(8L, "BRUFEN");
        stubSearch("brufen", match(brufen, 0.95));
        when(cartInteractionService.previewWarningsForCandidate(8L, "en"))
                .thenReturn(List.of(new InteractionWarningResponse(
                        "HIGH", "Blood thinner + NSAID", "Ask your doctor.", List.of())));
        when(modelClient.agentStep(anyString(), any(), anyInt())).thenReturn(
                step("search_catalog", "brufen", null, null),
                // The model goes straight for the add — the gate must stop it.
                step("add_to_cart", null, 8L, 1)
        );

        AgentOutcome outcome = service.run("add brufen", null, "en");

        assertThat(outcome.status()).isEqualTo(Status.ASK_USER);
        assertThat(outcome.reply()).contains("Blood thinner + NSAID");
        verify(cartService, never()).addItem(any(), anyString());
    }

    @Test
    void inventedProductIdsAreRejectedAndRepeatOffendersFallBack() {
        when(modelClient.agentStep(anyString(), any(), anyInt())).thenReturn(
                step("add_to_cart", null, 999L, 1),
                step("add_to_cart", null, 999L, 1)
        );

        AgentOutcome outcome = service.run("add something", null, "en");

        assertThat(outcome.status()).isEqualTo(Status.FALLBACK);
        verify(cartService, never()).addItem(any(), anyString());
    }

    @Test
    void stepCeilingAbortsToTheDeterministicPath() {
        stubSearch("panadol", match(product(7L, "PANADOL"), 0.9));
        when(modelClient.agentStep(anyString(), any(), anyInt()))
                .thenReturn(step("search_catalog", "panadol", null, null));

        AgentOutcome outcome = service.run("panadol", null, "en");

        assertThat(outcome.status()).isEqualTo(Status.FALLBACK);
        verify(cartService, never()).addItem(any(), anyString());
    }

    @Test
    void gatewayFailureFallsBackInsteadOfSurfacing() {
        when(modelClient.agentStep(anyString(), any(), anyInt()))
                .thenThrow(new ResponseStatusException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(service.run("panadol", null, "en").status()).isEqualTo(Status.FALLBACK);
    }

    @Test
    void doneReturnsTheModelsSummaryWithoutMutatingAnything() {
        stubSearch("unicorn tears", /* no matches */ new ProductMatchResponse[0]);
        when(modelClient.agentStep(anyString(), any(), anyInt())).thenReturn(
                step("search_catalog", "unicorn tears", null, null),
                step("done", null, null, null, null, "Nothing suitable in the catalog.")
        );

        AgentOutcome outcome = service.run("unicorn tears", null, "en");

        assertThat(outcome.status()).isEqualTo(Status.DONE);
        assertThat(outcome.reply()).isEqualTo("Nothing suitable in the catalog.");
        verify(cartService, never()).addItem(any(), anyString());
    }

    private void stubSearch(String query, ProductMatchResponse... matches) {
        when(catalogRagService.search(eq(query), eq("en"), anyInt()))
                .thenReturn(new CatalogSearchResponse(
                        query, "en", true, "p", "m", List.of(matches)));
    }

    private AgentStep step(String tool, String query, Long productId, Integer quantity) {
        return step(tool, query, productId, quantity, null, null);
    }

    private AgentStep step(
            String tool, String query, Long productId, Integer quantity,
            String question, String summary
    ) {
        return new AgentStep(tool, query, productId, quantity, question, summary);
    }

    private ProductResponse product(Long id, String name) {
        return new ProductResponse(
                id, name, name, "500mg", "20 tablets", "tablet", new BigDecimal("25.00"),
                "PARACETAMOL", "Analgesic", 1L, "Pain relief", "Company", "ORAL.SOLID",
                "Relieves pain", "https://example.com/image.png"
        );
    }

    private ProductMatchResponse match(ProductResponse product, double score) {
        return new ProductMatchResponse(product, score, score, score, "hybrid");
    }
}
