package com.example.dawanow.repo;

import com.example.dawanow.dtos.response.MedicineRequestItemResponse;
import com.example.dawanow.entity.RequestItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequestItemRepository extends JpaRepository<RequestItem, Long> {

    /**
     * Batched access to raw request line data. Populates every
     * MedicineRequestItemResponse except `product` and `unitPrice` (see
     * MedicineRequestItemResponse javadoc); MedicineRequestService resolves
     * the localized products in one call via ProductRepository.findAllLocalized
     * and fills them in with the setters.
     */
    @Query("""
            SELECT new com.example.dawanow.dtos.response.MedicineRequestItemResponse(
                ri.id, ri.request.id, ri.product.id, ri.quantity)
            FROM RequestItem ri
            WHERE ri.request.id IN :requestIds
            """)
    List<MedicineRequestItemResponse> findByRequestIdIn(@Param("requestIds") List<Long> requestIds);
}
