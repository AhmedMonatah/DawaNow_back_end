package com.example.dawanow.repo;

import com.example.dawanow.dtos.response.OrderItemResponse;
import com.example.dawanow.entity.OrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Batched access to raw order line data. Populates every OrderItemResponse
 * except `product` (see OrderItemResponse javadoc); OrderService resolves
 * the localized products in one call via ProductRepository.findAllLocalized
 * and fills them in with the setters.
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("""
            SELECT new com.example.dawanow.dtos.response.OrderItemResponse(
                oi.id, oi.order.id, oi.product.id, oi.quantity, oi.unitPrice)
            FROM OrderItem oi
            WHERE oi.order.id IN :orderIds
            """)
    List<OrderItemResponse> findByOrderIdIn(@Param("orderIds") List<Long> orderIds);
}
