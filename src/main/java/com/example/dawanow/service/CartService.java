package com.example.dawanow.service;

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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


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
    public CartResponse addItems(BulkAddCartItemsRequest request) {
        Map<Long, Long> quantitiesByProduct = new LinkedHashMap<>();
        for (AddCartItemRequest item : request.items()) {
            quantitiesByProduct.merge(item.productId(), item.quantity(), (current, addition) -> {
                try {
                    return Math.addExact(current, addition);
                } catch (ArithmeticException exception) {
                    throw new IllegalArgumentException("Cart item quantity is too large");
                }
            });
        }

        List<Product> products = productRepository.findAllById(quantitiesByProduct.keySet());
        Map<Long, Product> productsById = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (Long productId : quantitiesByProduct.keySet()) {
            if (!productsById.containsKey(productId)) {
                throw new ResourceNotFoundException("Product not found: " + productId);
            }
        }

        Cart cart = getOrCreateCart();
        Map<Long, CartItem> existingItems = cart.getItems().stream()
                .collect(Collectors.toMap(item -> item.getProduct().getId(), Function.identity()));

        for (Map.Entry<Long, Long> entry : quantitiesByProduct.entrySet()) {
            CartItem cartItem = existingItems.get(entry.getKey());
            if (cartItem == null) {
                Product product = productsById.get(entry.getKey());
                cartItem = new CartItem();
                cartItem.setCart(cart);
                cartItem.setProduct(product);
                cartItem.setQuantity(entry.getValue());
                cartItem.setUnitPrice(product.getPrice());
                cart.getItems().add(cartItem);
                cartItemRepository.save(cartItem);
            } else {
                try {
                    cartItem.setQuantity(Math.addExact(cartItem.getQuantity(), entry.getValue()));
                } catch (ArithmeticException exception) {
                    throw new IllegalArgumentException("Cart item quantity is too large");
                }
            }
        }

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
