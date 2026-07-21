package com.example.dawanow.service;

import com.example.dawanow.dtos.request.AddCartItemRequest;
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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;


@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final CurrentUserProvider currentUserProvider;
    private final CartMapper cartMapper;


    @Transactional
    public CartResponse getCart(){
        Cart cart = getOrCreateCart();
        return cartMapper.toResponse(cart);
    }

    @Transactional
    public Cart getCartEntity(){
        Cart cart = getOrCreateCart();
        return cart;
    }


    @Transactional
    public CartResponse addItem(AddCartItemRequest request){
        Cart cart = getOrCreateCart();
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        CartItem cartItem = cartItemRepository.findByCartIdAndProductId(cart.getId(),request.productId()).orElse(null);

            if(cartItem == null){
            cartItem = new CartItem();
            cartItem.setCart(cart);
            cartItem.setProduct(product);
            cartItem.setQuantity(request.quantity());
            cartItem.setUnitPrice(product.getPrice());
            cart.getItems().add(cartItem);
            cartItemRepository.save(cartItem);
            }
            else cartItem.setQuantity(cartItem.getQuantity() + request.quantity());

        recalculateCartTotal(cart);

        return cartMapper.toResponse(cart);
    }

    @Transactional
    public CartResponse setQuantity(Long cartItemId, Long newQuantity){
        Cart cart = getOrCreateCart();
        CartItem cartItem = cartItemRepository.findByIdAndCartId(cartItemId, cart.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));
        cartItem.setQuantity(newQuantity);
        recalculateCartTotal(cart);
        return cartMapper.toResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(Long cartItemId){
        Cart cart = getOrCreateCart();
        CartItem cartItem = cartItemRepository.findByIdAndCartId(cartItemId, cart.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));
        cart.getItems().remove(cartItem);
        recalculateCartTotal(cart);
        return cartMapper.toResponse(cart);
    }

    @Transactional
    public void clearCart(){
        Cart cart = getOrCreateCart();
        cart.getItems().clear();
        cart.setTotalPrice(BigDecimal.ZERO);
    }

    @Transactional
    public Long getItemCount(){
        Cart cart = getOrCreateCart();
        return cart.getItems().stream().mapToLong(CartItem::getQuantity).sum();
    }

    private Cart getOrCreateCart() {
        User currentUser = currentUserProvider.get();

        return cartRepository.findByUserId(currentUser.getId())
                .orElseGet(() -> {
                    Cart newCart = new Cart();
                    newCart.setUser(currentUser);
                    newCart.setTotalPrice(BigDecimal.ZERO);
                     return cartRepository.save(newCart);
                });
    }
    private void recalculateCartTotal(Cart cart) {
        BigDecimal total = cart.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        cart.setTotalPrice(total);
    }

}