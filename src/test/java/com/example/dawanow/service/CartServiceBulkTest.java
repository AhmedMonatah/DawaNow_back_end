package com.example.dawanow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.dawanow.dtos.request.AddCartItemRequest;
import com.example.dawanow.dtos.request.BulkAddCartItemsRequest;
import com.example.dawanow.dtos.response.CartResponse;
import com.example.dawanow.entity.Cart;
import com.example.dawanow.entity.CartItem;
import com.example.dawanow.entity.Product;
import com.example.dawanow.entity.User;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.mapper.CartMapper;
import com.example.dawanow.repo.CartItemRepository;
import com.example.dawanow.repo.CartRepository;
import com.example.dawanow.repo.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CartServiceBulkTest {

    private CartRepository cartRepository;
    private CartItemRepository cartItemRepository;
    private ProductRepository productRepository;
    private CurrentUserProvider currentUserProvider;
    private CartMapper cartMapper;
    private CartService service;

    @BeforeEach
    void setUp() {
        cartRepository = mock(CartRepository.class);
        cartItemRepository = mock(CartItemRepository.class);
        productRepository = mock(ProductRepository.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        cartMapper = mock(CartMapper.class);
        service = new CartService(
                cartRepository,
                cartItemRepository,
                productRepository,
                currentUserProvider,
                cartMapper
        );
    }

    @Test
    void consolidatesDuplicatesAndIncreasesExistingQuantity() {
        Product product = product(11L, "10.00");
        Cart cart = new Cart();
        cart.setId(5L);
        CartItem existing = new CartItem();
        existing.setCart(cart);
        existing.setProduct(product);
        existing.setQuantity(4L);
        existing.setUnitPrice(product.getPrice());
        cart.getItems().add(existing);
        User user = new User();
        user.setId(7L);

        when(productRepository.findAllById(any())).thenReturn(List.of(product));
        when(currentUserProvider.get()).thenReturn(user);
        when(cartRepository.findByUserId(7L)).thenReturn(Optional.of(cart));
        CartResponse mapped = new CartResponse(5L, List.of(), new BigDecimal("70.00"));
        when(cartMapper.toResponse(cart)).thenReturn(mapped);

        CartResponse result = service.addItems(new BulkAddCartItemsRequest(List.of(
                new AddCartItemRequest(11L, 1L),
                new AddCartItemRequest(11L, 2L)
        )));

        assertThat(result).isEqualTo(mapped);
        assertThat(existing.getQuantity()).isEqualTo(7L);
        assertThat(cart.getTotalPrice()).isEqualByComparingTo("70.00");
    }

    @Test
    void addsAllNewProductsAndRecalculatesOnce() {
        Product first = product(11L, "10.00");
        Product second = product(12L, "5.00");
        Cart cart = new Cart();
        cart.setId(5L);
        User user = new User();
        user.setId(7L);

        when(productRepository.findAllById(any())).thenReturn(List.of(first, second));
        when(currentUserProvider.get()).thenReturn(user);
        when(cartRepository.findByUserId(7L)).thenReturn(Optional.of(cart));

        service.addItems(new BulkAddCartItemsRequest(List.of(
                new AddCartItemRequest(11L, 2L),
                new AddCartItemRequest(12L, 3L)
        )));

        assertThat(cart.getItems()).hasSize(2);
        assertThat(cart.getTotalPrice()).isEqualByComparingTo("35.00");
        verify(cartItemRepository, org.mockito.Mockito.times(2)).save(any(CartItem.class));
    }

    @Test
    void validatesEveryProductBeforeTouchingCart() {
        when(productRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.addItems(new BulkAddCartItemsRequest(List.of(
                new AddCartItemRequest(99L, 1L)
        ))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product not found: 99");

        verifyNoInteractions(cartRepository, cartItemRepository, currentUserProvider, cartMapper);
    }

    private Product product(Long id, String price) {
        Product product = new Product();
        product.setId(id);
        product.setPrice(new BigDecimal(price));
        product.setName("Product " + id);
        return product;
    }
}
