package com.example.dawanow.repo;

import com.example.dawanow.dtos.response.PharmacyOfferItemResponse;
import com.example.dawanow.entity.PharmacyOfferItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PharmacyOfferItemRepository extends JpaRepository<PharmacyOfferItem, Long> {

    @EntityGraph(attributePaths = {
            "offer",
            "offer.request",
            "offer.pharmacy",
            "offer.pharmacist",
            "requestItem",
            "requestItem.product"
    })
    List<PharmacyOfferItem> findByRequestItemIdIn(Collection<Long> ids);
    Optional<PharmacyOfferItem> findByRequestItemId(Long requestItemId);

    /**
     * Batched access to raw offer line data. Populates every
     * PharmacyOfferItemResponse except `product` (see
     * PharmacyOfferItemResponse javadoc); PharmacyOfferService resolves the
     * localized products in one call via ProductRepository.findAllLocalized
     * and fills them in with the setters.
     */
    @Query("""
            SELECT new com.example.dawanow.dtos.response.PharmacyOfferItemResponse(
                poi.id, poi.offer.id, poi.requestItem.id, poi.product.id)
            FROM PharmacyOfferItem poi
            WHERE poi.offer.id IN :offerIds
            """)
    List<PharmacyOfferItemResponse> findByOfferIdIn(@Param("offerIds") List<Long> offerIds);

}
