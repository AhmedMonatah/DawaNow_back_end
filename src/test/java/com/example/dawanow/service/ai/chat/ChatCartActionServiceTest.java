package com.example.dawanow.service.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.dawanow.config.AiChatProperties;
import com.example.dawanow.controller.CatalogAiController.CatalogSearchResponse;
import com.example.dawanow.controller.CatalogAiController.ProductMatchResponse;
import com.example.dawanow.dtos.request.AddCartItemRequest;
import com.example.dawanow.dtos.response.CartResponse;
import com.example.dawanow.dtos.response.ProductResponse;
import com.example.dawanow.service.CartService;
import com.example.dawanow.service.ai.chat.ChatCartActionService.CartActionOutcome;
import com.example.dawanow.service.ai.chat.ChatCartActionService.Status;
import com.example.dawanow.service.ai.interactions.CartInteractionService;
import com.example.dawanow.service.ai.rag.CatalogRagService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatCartActionServiceTest {

    @Mock
    private CartService cartService;
    @Mock
    private CatalogRagService catalogRagService;
    @Mock
    private CartInteractionService cartInteractionService;

    private ChatCartActionService service;

    @BeforeEach
    void setUp() {
        service = new ChatCartActionService(
                cartService, catalogRagService, cartInteractionService, new AiChatProperties());
    }

    @Test
    void confidentMatchAddsToCartForReal() {
        ProductResponse panadol = product(7L, "PANADOL ADVANCE");
        stubSearch(match(panadol, 0.95, "hybrid"), match(product(8L, "PANADOL EXTRA"), 0.60, "hybrid"));
        when(cartService.addItem(new AddCartItemRequest(7L, 2L), "en"))
                .thenReturn(new CartResponse(1L, List.of(), BigDecimal.TEN));
        when(cartInteractionService.warningsInvolving(7L, "en")).thenReturn(List.of());

        CartActionOutcome outcome = service.addToCart("panadol", 2, "en");

        assertThat(outcome.status()).isEqualTo(Status.ADDED);
        assertThat(outcome.product().id()).isEqualTo(7L);
        assertThat(outcome.quantity()).isEqualTo(2);
        verify(cartService).addItem(new AddCartItemRequest(7L, 2L), "en");
    }

    @Test
    void exactNameMatchIsTrustedEvenWithLowScore() {
        ProductResponse panadol = product(7L, "PANADOL ADVANCE");
        stubSearch(match(panadol, 0.55, "exact-name"), match(product(8L, "OTHER"), 0.54, "hybrid"));
        when(cartService.addItem(new AddCartItemRequest(7L, 1L), "en"))
                .thenReturn(new CartResponse(1L, List.of(), BigDecimal.TEN));
        when(cartInteractionService.warningsInvolving(7L, "en")).thenReturn(List.of());

        assertThat(service.addToCart("panadol advance", null, "en").status()).isEqualTo(Status.ADDED);
    }

    @Test
    void closeScoresAskTheUserInsteadOfGuessing() {
        stubSearch(
                match(product(7L, "PANADOL ADVANCE"), 0.85, "hybrid"),
                match(product(8L, "PANADOL EXTRA"), 0.83, "hybrid"),
                match(product(9L, "PANADOL MIGRAINE"), 0.80, "hybrid")
        );

        CartActionOutcome outcome = service.addToCart("panadol", 1, "en");

        assertThat(outcome.status()).isEqualTo(Status.AMBIGUOUS);
        assertThat(outcome.candidates()).hasSize(3);
        verifyNoInteractions(cartService);
    }

    @Test
    void lowScoreIsAmbiguousNotAutoAdded() {
        stubSearch(match(product(7L, "SOMETHING"), 0.45, "semantic"));

        assertThat(service.addToCart("vague words", 1, "en").status()).isEqualTo(Status.AMBIGUOUS);
        verifyNoInteractions(cartService);
    }

    @Test
    void blankQueryAndNoMatchesReturnNotFound() {
        assertThat(service.addToCart("  ", 1, "en").status()).isEqualTo(Status.NOT_FOUND);

        when(catalogRagService.search(anyString(), anyString(), anyInt()))
                .thenReturn(new CatalogSearchResponse("q", "en", true, "p", "m", List.of()));
        assertThat(service.addToCart("unknown medicine", 1, "en").status()).isEqualTo(Status.NOT_FOUND);
        verifyNoInteractions(cartService);
    }

    @Test
    void quantityIsClampedToSaneBounds() {
        ProductResponse panadol = product(7L, "PANADOL ADVANCE");
        stubSearch(match(panadol, 0.95, "hybrid"));
        when(cartService.addItem(new AddCartItemRequest(7L, 20L), "en"))
                .thenReturn(new CartResponse(1L, List.of(), BigDecimal.TEN));
        when(cartInteractionService.warningsInvolving(7L, "en")).thenReturn(List.of());

        assertThat(service.addToCart("panadol", 999, "en").quantity()).isEqualTo(20);
    }

    @Test
    void interactionCheckFailureNeverBlocksTheAdd() {
        ProductResponse panadol = product(7L, "PANADOL ADVANCE");
        stubSearch(match(panadol, 0.95, "hybrid"));
        when(cartService.addItem(new AddCartItemRequest(7L, 1L), "en"))
                .thenReturn(new CartResponse(1L, List.of(), BigDecimal.TEN));
        when(cartInteractionService.warningsInvolving(7L, "en"))
                .thenThrow(new IllegalStateException("boom"));

        CartActionOutcome outcome = service.addToCart("panadol", 1, "en");

        assertThat(outcome.status()).isEqualTo(Status.ADDED);
        assertThat(outcome.interactionWarnings()).isEmpty();
    }

    private void stubSearch(ProductMatchResponse... matches) {
        when(catalogRagService.search(anyString(), eq("en"), anyInt()))
                .thenReturn(new CatalogSearchResponse("q", "en", true, "p", "m", List.of(matches)));
    }

    private ProductResponse product(Long id, String name) {
        return new ProductResponse(
                id, name, name, "500mg", "20 tablets", "tablet", new BigDecimal("25.00"),
                "PARACETAMOL", "Analgesic", 1L, "Pain relief", "Company", "ORAL.SOLID",
                "Relieves pain", "https://example.com/image.png"
        );
    }

    private ProductMatchResponse match(ProductResponse product, double score, String reason) {
        return new ProductMatchResponse(product, score, score, score, reason);
    }
}
