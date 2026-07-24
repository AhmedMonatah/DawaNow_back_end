package com.example.dawanow.service;

import com.example.dawanow.entity.PharmacyOfferItem;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PharmacySelectionOptimizer {

    private Map<Long, List<PharmacyOfferItem>> candidatesByRequestItem;
    private Map<Long, Set<Long>> coverageByPharmacy;
    private Set<String> visited;
    private List<Long> bestPharmacyIds;
    private List<PharmacyOfferItem> bestItems;
    private BigDecimal bestTotal;

    public synchronized List<PharmacyOfferItem> optimize(List<PharmacyOfferItem> selectedOfferItems) {
        if (selectedOfferItems == null || selectedOfferItems.isEmpty()) {
            throw new IllegalArgumentException("At least one selected offer item is required");
        }

        candidatesByRequestItem = groupByRequestItem(selectedOfferItems);
        coverageByPharmacy = buildCoverageByPharmacy();
        visited = new HashSet<>();
        bestPharmacyIds = null;
        bestItems = null;
        bestTotal = null;

        explore(new LinkedHashSet<>());
        if (bestItems == null) {
            throw new IllegalArgumentException("The selected choices cannot fulfill the selected medicines");
        }
        return bestItems;
    }

    private Map<Long, List<PharmacyOfferItem>> groupByRequestItem(List<PharmacyOfferItem> selectedOfferItems) {
        Map<Long, List<PharmacyOfferItem>> groupedItems = new HashMap<>();
        for (PharmacyOfferItem selectedOfferItem : selectedOfferItems) {
            Long requestItemId = selectedOfferItem.getRequestItem().getId();
            groupedItems
                    .computeIfAbsent(requestItemId, ignored -> new ArrayList<>())
                    .add(selectedOfferItem);
        }
        return groupedItems;
    }

    private Map<Long, Set<Long>> buildCoverageByPharmacy() {
        Map<Long, Set<Long>> coverage = new HashMap<>();
        for (Map.Entry<Long, List<PharmacyOfferItem>> entry : candidatesByRequestItem.entrySet()) {
            for (PharmacyOfferItem candidate : entry.getValue()) {
                Long pharmacyId = candidate.getOffer().getPharmacy().getId();
                coverage.computeIfAbsent(pharmacyId, ignored -> new HashSet<>()).add(entry.getKey());
            }
        }
        return coverage;
    }

    private void explore(Set<Long> chosenPharmacies) {
        List<Long> sortedChosen = chosenPharmacies.stream().sorted().toList();
        if (!visited.add(sortedChosen.toString())) {
            return;
        }
        if (bestPharmacyIds != null && chosenPharmacies.size() > bestPharmacyIds.size()) {
            return;
        }

        Set<Long> covered = coveredRequestItems(chosenPharmacies);
        if (covered.containsAll(candidatesByRequestItem.keySet())) {
            List<PharmacyOfferItem> candidateItems = buildItems(sortedChosen);
            BigDecimal candidateTotal = total(candidateItems);
            if (isBetter(sortedChosen, candidateItems, candidateTotal)) {
                bestPharmacyIds = List.copyOf(sortedChosen);
                bestItems = List.copyOf(candidateItems);
                bestTotal = candidateTotal;
            }
            return;
        }
        if (bestPharmacyIds != null && chosenPharmacies.size() >= bestPharmacyIds.size()) {
            return;
        }

        Long constrainedRequestItem = candidatesByRequestItem.keySet().stream()
                .filter(id -> !covered.contains(id))
                .min(Comparator
                        .comparingInt((Long id) -> candidatePharmacyIds(id).size())
                        .thenComparingLong(Long::longValue))
                .orElseThrow();

        List<Long> options = candidatePharmacyIds(constrainedRequestItem).stream()
                .sorted(Comparator
                        .comparingInt((Long pharmacyId) -> newlyCoveredCount(pharmacyId, covered))
                        .reversed()
                        .thenComparingLong(Long::longValue))
                .toList();

        for (Long pharmacyId : options) {
            if (chosenPharmacies.add(pharmacyId)) {
                explore(chosenPharmacies);
                chosenPharmacies.remove(pharmacyId);
            }
        }
    }

    private Set<Long> coveredRequestItems(Set<Long> chosenPharmacies) {
        Set<Long> covered = new HashSet<>();
        for (Long pharmacyId : chosenPharmacies) {
            covered.addAll(coverageByPharmacy.getOrDefault(pharmacyId, Set.of()));
        }
        return covered;
    }

    private Set<Long> candidatePharmacyIds(Long requestItemId) {
        Set<Long> pharmacyIds = new HashSet<>();
        for (PharmacyOfferItem candidate : candidatesByRequestItem.get(requestItemId)) {
            pharmacyIds.add(candidate.getOffer().getPharmacy().getId());
        }
        return pharmacyIds;
    }

    private int newlyCoveredCount(Long pharmacyId, Set<Long> alreadyCovered) {
        int count = 0;
        for (Long requestItemId : coverageByPharmacy.getOrDefault(pharmacyId, Set.of())) {
            if (!alreadyCovered.contains(requestItemId)) {
                count++;
            }
        }
        return count;
    }

    private List<PharmacyOfferItem> buildItems(List<Long> pharmacyIds) {
        Set<Long> chosen = Set.copyOf(pharmacyIds);
        List<PharmacyOfferItem> selectedItems = new ArrayList<>();

        for (Long requestItemId : candidatesByRequestItem.keySet().stream().sorted().toList()) {
            PharmacyOfferItem bestItem = candidatesByRequestItem.get(requestItemId).stream()
                    .filter(item -> chosen.contains(item.getOffer().getPharmacy().getId()))
                    .min(itemComparator())
                    .orElseThrow();
            selectedItems.add(bestItem);
        }

        return selectedItems;
    }

    private Comparator<PharmacyOfferItem> itemComparator() {
        return Comparator
                .comparing(this::lineTotal)
                .thenComparing(item -> item.getOffer().getPharmacy().getId())
                .thenComparing(PharmacyOfferItem::getId);
    }

    private boolean isBetter(
            List<Long> candidatePharmacyIds,
            List<PharmacyOfferItem> candidateItems,
            BigDecimal candidateTotal
    ) {
        if (bestItems == null) {
            return true;
        }
        int pharmacyCount = Integer.compare(candidatePharmacyIds.size(), bestPharmacyIds.size());
        if (pharmacyCount != 0) {
            return pharmacyCount < 0;
        }
        int totalPrice = candidateTotal.compareTo(bestTotal);
        if (totalPrice != 0) {
            return totalPrice < 0;
        }
        for (int index = 0; index < candidatePharmacyIds.size(); index++) {
            int idComparison = candidatePharmacyIds.get(index).compareTo(bestPharmacyIds.get(index));
            if (idComparison != 0) {
                return idComparison < 0;
            }
        }
        return firstItemId(candidateItems) < firstItemId(bestItems);
    }

    private BigDecimal total(List<PharmacyOfferItem> items) {
        return items.stream()
                .map(this::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal lineTotal(PharmacyOfferItem item) {
        return item.getRequestItem().getProduct().getPrice()
                .multiply(BigDecimal.valueOf(item.getRequestItem().getQuantity()));
    }

    private Long firstItemId(List<PharmacyOfferItem> items) {
        return items.stream()
                .map(PharmacyOfferItem::getId)
                .min(Long::compareTo)
                .orElse(Long.MAX_VALUE);
    }
}
