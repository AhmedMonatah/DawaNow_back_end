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
     * Whether any offer item other than the given offer already offered the
     * product as an alternative for the same request item. Used to tell apart
     * genuinely new alternatives (nothing sent yet) from repeats.
     */
    @Query("""
            select case when count(poi) > 0 then true else false end
            from PharmacyOfferItem poi
            where poi.requestItem.id = :requestItemId
              and poi.product.id = :productId
              and poi.alternative = true
              and poi.offer.id <> :excludedOfferId
            """)
    boolean existsPriorAlternative(
            @Param("requestItemId") Long requestItemId,
            @Param("productId") Long productId,
            @Param("excludedOfferId") Long excludedOfferId);

    /**
     * Distinct (requestItemId, productId) pairs of all alternative offer items
     * for the given request items, used to render the alternatives of each
     * ALTERNATIVE_FOUND item in the request result.
     */
    @Query("""
            select distinct poi.requestItem.id, poi.product.id
            from PharmacyOfferItem poi
            where poi.requestItem.id in :requestItemIds
              and poi.alternative = true
            """)
    List<Object[]> findAlternativeProductIdsByRequestItemIdIn(
            @Param("requestItemIds") Collection<Long> requestItemIds);

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
