package com.example.dawanow.repo;

import com.example.dawanow.entity.PharmacyOfferItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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

}
