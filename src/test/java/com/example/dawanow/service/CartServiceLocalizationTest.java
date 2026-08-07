package com.example.dawanow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.dawanow.dtos.response.CartItemResponse;
import com.example.dawanow.dtos.response.CartResponse;
import com.example.dawanow.dtos.response.ProductSummaryResponse;
import com.example.dawanow.entity.Cart;
import com.example.dawanow.entity.User;
import com.example.dawanow.mapper.CartMapper;
import com.example.dawanow.repo.CartItemRepository;
import com.example.dawanow.repo.CartRepository;
import com.example.dawanow.repo.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CartServiceLocalizationTest {

    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private CartMapper cartMapper;

    private CartService service;

    @BeforeEach
    void setUp() {
        service = new CartService(
                cartRepository, cartItemRepository, productRepository,
                currentUserProvider, cartMapper
        );
    }

    @Test
    void resolvesLocalizedProductsForCartItems() {
        User user = new User();
        user.setId(7L);
        Cart cart = new Cart();
        cart.setId(5L);
        when(currentUserProvider.get()).thenReturn(user);
        when(cartRepository.findByUserId(7L)).thenReturn(Optional.of(cart));

        CartItemResponse item = new CartItemResponse(1L, 5L, 11L, new BigDecimal("10.00"), 2L);
        when(cartItemRepository.findByCartIdIn(List.of(5L))).thenReturn(List.of(item));

        ProductSummaryResponse summary = new ProductSummaryResponse(
                11L, "بانادول", "بانادول", null, null, null, new BigDecimal("10.00"),
                "باراسيتامول", null, null, null, "img.png");
        when(productRepository.findAllLocalized(List.of(11L), "ar", "en")).thenReturn(List.of(summary));

        CartResponse expected = new CartResponse(5L, List.of(item), new BigDecimal("20.00"));
        when(cartMapper.toResponse(cart, List.of(item))).thenReturn(expected);

        CartResponse result = service.getCart("ar");

        assertThat(result).isEqualTo(expected);
        assertThat(item.getProduct()).isSameAs(summary);
        assertThat(item.getSubtotal()).isEqualByComparingTo("20.00");
    }

    @Test
    void leavesProductNullWhenItemHasNoProductId() {
        User user = new User();
        user.setId(7L);
        Cart cart = new Cart();
        cart.setId(5L);
        when(currentUserProvider.get()).thenReturn(user);
        when(cartRepository.findByUserId(7L)).thenReturn(Optional.of(cart));

        CartItemResponse item = new CartItemResponse(1L, 5L, null, new BigDecimal("10.00"), 2L);
        when(cartItemRepository.findByCartIdIn(List.of(5L))).thenReturn(List.of(item));

        CartResponse expected = new CartResponse(5L, List.of(item), new BigDecimal("20.00"));
        when(cartMapper.toResponse(cart, List.of(item))).thenReturn(expected);

        CartResponse result = service.getCart("ar");

        assertThat(result).isEqualTo(expected);
        assertThat(item.getProduct()).isNull();
    }
}
