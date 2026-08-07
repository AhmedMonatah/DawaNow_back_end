package com.example.dawanow.repo;

import com.example.dawanow.dtos.response.CartItemResponse;
import com.example.dawanow.entity.CartItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);
    Optional<CartItem> findByIdAndCartId(Long cartItemId, Long cartId);

    /**
     * Batched access to raw cart line data. Populates every CartItemResponse
     * except `product` (see CartItemResponse javadoc); CartService resolves
     * the localized products in one call via ProductRepository.findAllLocalized
     * and fills them in with the setters.
     */
    @Query("""
            SELECT new com.example.dawanow.dtos.response.CartItemResponse(
                ci.id, ci.cart.id, ci.product.id, ci.unitPrice, ci.quantity)
            FROM CartItem ci
            WHERE ci.cart.id IN :cartIds
            """)
    List<CartItemResponse> findByCartIdIn(@Param("cartIds") List<Long> cartIds);
}
