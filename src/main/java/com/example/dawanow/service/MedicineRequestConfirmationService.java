package com.example.dawanow.service;

import com.example.dawanow.dtos.request.ConfirmSelectionRequest;
import com.example.dawanow.dtos.response.ConfirmationResponse;
import com.example.dawanow.dtos.response.OrderSummaryResponse;
import com.example.dawanow.entity.*;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.factory.NotificationFactory;
import com.example.dawanow.repo.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MedicineRequestConfirmationService {

    private final MedicineRequestRepository medicineRequestRepository;
    private final PharmacyOfferRepository pharmacyOfferRepository;
    private final PharmacyOfferItemRepository pharmacyOfferItemRepository;
    private final OrderRepository orderRepository;
    private final MasterOrderRepository masterOrderRepository;
    private final CurrentUserProvider currentUserProvider;
    private final PharmacySelectionOptimizer selectionOptimizer;
    private final NotificationService notificationService;
    private final NotificationFactory notificationFactory;
    private final RequestResultSseService requestResultSseService;

    @Transactional
    public ConfirmationResponse confirm(Long requestId, ConfirmSelectionRequest selection) {
        Customer customer = requireCurrentCustomer();
        MedicineRequest medicineRequest = medicineRequestRepository.findDetailedById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicine request not found"));

        validateRequestCanBeConfirmed(medicineRequest, customer);
        if (masterOrderRepository.existsByRequestId(medicineRequest.getId())) {
            throw new IllegalArgumentException("This medicine request has already been confirmed");
        }

        LinkedHashSet<Long> selectedIds = new LinkedHashSet<>(selection.selectedItems()
                .stream().map(ConfirmSelectionRequest.SelectedItem::requestItemId).toList());
        if (selectedIds.size() != selection.selectedItems().size()) {
            throw new IllegalArgumentException("Selected request item IDs must be unique");
        }

        Map<Long, Long> productIdByRequestItem = selection.selectedItems().stream()
                .collect(Collectors.toMap(
                        ConfirmSelectionRequest.SelectedItem::requestItemId,
                        ConfirmSelectionRequest.SelectedItem::productId));

        List<PharmacyOfferItem> offerItems = pharmacyOfferItemRepository.findByRequestItemIdIn(selectedIds);

        Set<Long> itemsWithOffers = offerItems.stream()
                .map(item -> item.getRequestItem().getId())
                .collect(Collectors.toSet());

        if (itemsWithOffers.size() != selectedIds.size()) {
            throw new ResourceNotFoundException("some request items do not have corresponding offers");
        }

        List<PharmacyOfferItem> selectedOfferItems = selectChosenProducts(offerItems, productIdByRequestItem);

        validateSelectedItems(medicineRequest, selectedOfferItems);
        FulfillmentMethod fulfillmentMethod = selection.fulfillmentMethod();



        MasterOrder masterOrder = new MasterOrder();
        masterOrder.setFulfillmentMethod(fulfillmentMethod);
        masterOrder.setUser(customer);
        masterOrder.setRequest(medicineRequest);

        PaymentMethod paymentMethod = medicineRequest.getPaymentMethod();
        masterOrder.setPaymentMethod(paymentMethod);

        if (paymentMethod == PaymentMethod.CARD) {
            masterOrder.setPaymentStatus(PaymentStatus.PENDING);
            masterOrder.setOrderStatus(OrderStatus.PENDING_PAYMENT);
        } else {
            masterOrder.setPaymentStatus(null);
            masterOrder.setPaymentIntentId(null);
            masterOrder.setPaidAt(null);
        }



        List<PharmacyOfferItem> optimizedItems = selectionOptimizer.optimize(selectedOfferItems);
        List<Order> orders = createOrders(medicineRequest, optimizedItems);

        orders.forEach(masterOrder::addCustomerOrder);

        masterOrder.setTotalPrice(orders.stream().map(Order::getTotalPrice).reduce(BigDecimal.ZERO, BigDecimal::add));

        updateOfferStatuses(medicineRequest.getId(), optimizedItems);
        medicineRequest.setStatus(RequestStatus.COMPLETED);

        masterOrderRepository.save(masterOrder);

        orders.forEach(order -> notificationService.sendToPharmacy(
                notificationFactory.orderCreated(order),
                order.getId()
        ));

        requestResultSseService.closeForRequest(requestId);



        List<OrderSummaryResponse> summaries = orders.stream()
                .sorted(Comparator.comparing(order -> order.getPharmacy().getId()))
                .map(this::toSummary)
                .toList();
        return new ConfirmationResponse(medicineRequest.getId(), summaries);
    }

    private void validateRequestCanBeConfirmed(MedicineRequest medicineRequest, Customer customer) {
        if (!medicineRequest.getCustomer().getId().equals(customer.getId())) {
            throw new AccessDeniedException("You can only confirm your own medicine request");
        }
        if (medicineRequest.getStatus() != RequestStatus.PENDING) {
            throw new IllegalArgumentException("Only pending medicine requests can be confirmed");
        }
    }

    private List<PharmacyOfferItem> selectChosenProducts(
            List<PharmacyOfferItem> offerItems,
            Map<Long, Long> productIdByRequestItem
    ) {
        List<PharmacyOfferItem> selected = new ArrayList<>();
        for (PharmacyOfferItem item : offerItems) {
            Long chosenProductId = productIdByRequestItem.get(item.getRequestItem().getId());
            if (chosenProductId != null && item.getProduct() != null
                    && chosenProductId.equals(item.getProduct().getId())) {
                selected.add(item);
            }
        }
        log.info("Selected {} offer items for confirmation", selected.size());
        log.info("Selected offer items: {}", selected.stream()
                .map(item -> String.format("requestItemId=%d, productId=%d",
                        item.getRequestItem().getId(), item.getProduct().getId()))
                .collect(Collectors.joining(", ")));

        Set<Long> selectedRequestItemIds = selected.stream()
                .map(item -> item.getRequestItem().getId())
                .collect(Collectors.toSet());
        if (!selectedRequestItemIds.containsAll(productIdByRequestItem.keySet())) {
            throw new IllegalArgumentException(
                    "The chosen product must be offered for every selected request item"
            );
        }
        return selected;
    }

    private void validateSelectedItems(
            MedicineRequest medicineRequest,
            List<PharmacyOfferItem> selectedItems
    ) {        Map<Long, Long> offerByPharmacy = new HashMap<>();

        for (PharmacyOfferItem item : selectedItems) {
            PharmacyOffer offer = item.getOffer();
            if (!offer.getRequest().getId().equals(medicineRequest.getId())) {
                throw new IllegalArgumentException("Every selected offer item must belong to this medicine request");
            }
            if (offer.getStatus() == OfferStatus.REJECTED || offer.getStatus() == OfferStatus.EXPIRED) {
                throw new IllegalArgumentException("Rejected or expired offers cannot be selected");
            }

            validateOfferItem(item);
            validateOfferPharmacist(offer);

            Long pharmacyId = offer.getPharmacy().getId();
            Long existingOfferId = offerByPharmacy.putIfAbsent(pharmacyId, offer.getId());
            if (existingOfferId != null && !existingOfferId.equals(offer.getId())) {
                throw new IllegalArgumentException(
                        "A pharmacy must have only one offer for the same medicine request"
                );
            }
        }
    }

    private void validateOfferItem(PharmacyOfferItem item) {
        RequestItem requestItem = item.getRequestItem();
        Product product = item.getProduct();
        if (requestItem.getQuantity() == null || requestItem.getQuantity() <= 0) {
            throw new IllegalArgumentException("Requested quantity must be positive");
        }
        if (product == null || product.getPrice() == null || product.getPrice().signum() <= 0) {
            throw new IllegalArgumentException("The selected product price must be positive");
        }
    }

    private void validateOfferPharmacist(PharmacyOffer offer) {
        Pharmacist pharmacist = offer.getPharmacist();
        if (pharmacist == null || pharmacist.getPharmacy() == null
                || !pharmacist.getPharmacy().getId().equals(offer.getPharmacy().getId())) {
            throw new IllegalArgumentException("Each chosen offer must have a pharmacist from its pharmacy");
        }
    }

    private List<Order> createOrders(
            MedicineRequest medicineRequest,
            List<PharmacyOfferItem> selectedItems) {
        Map<Long, List<PharmacyOfferItem>> itemsByPharmacy = new LinkedHashMap<>();
        for (PharmacyOfferItem selectedItem : selectedItems) {
            Long pharmacyId = selectedItem.getOffer().getPharmacy().getId();
            itemsByPharmacy.computeIfAbsent(pharmacyId, ignored -> new ArrayList<>()).add(selectedItem);
        }

        List<Order> orders = new ArrayList<>();
        for (Map.Entry<Long, List<PharmacyOfferItem>> entry
                : itemsByPharmacy.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            List<PharmacyOfferItem> pharmacyItems = entry.getValue();
            PharmacyOffer offer = pharmacyItems.getFirst().getOffer();

            Order order = new Order();
            order.setUser(medicineRequest.getCustomer());
            order.setPharmacy(offer.getPharmacy());
            order.setPharmacist(offer.getPharmacist());
            order.setOffer(offer);
            order.setDeliveryLatitude(medicineRequest.getDeliveryLatitude());
            order.setDeliveryLongitude(medicineRequest.getDeliveryLongitude());
            order.setStatus(OrderStatus.PREPARING);
            order.setDate(LocalDateTime.now());
            order.setRequest(medicineRequest);


            BigDecimal total = BigDecimal.ZERO;
            for (PharmacyOfferItem selectedItem : pharmacyItems) {
                Product product = selectedItem.getProduct();
                Long quantity = selectedItem.getRequestItem().getQuantity();
                OrderItem orderItem = new OrderItem();
                orderItem.setOrder(order);
                orderItem.setProduct(product);
                orderItem.setQuantity(quantity);
                orderItem.setUnitPrice(product.getPrice());
                order.getItems().add(orderItem);
                total = total.add(
                        product.getPrice().multiply(BigDecimal.valueOf(quantity))
                );
            }
            order.setTotalPrice(total);
            orders.add(order);
        }
        return orders;
    }

    private void updateOfferStatuses(
            Long requestId,
            List<PharmacyOfferItem> selectedItems
    ) {
        Map<Long, Set<Long>> selectedItemIdsByOffer = new HashMap<>();
        for (PharmacyOfferItem offerItem : selectedItems) {
            selectedItemIdsByOffer
                    .computeIfAbsent(offerItem.getOffer().getId(), ignored -> new java.util.HashSet<>())
                    .add(offerItem.getId());
        }

        for (PharmacyOffer offer : pharmacyOfferRepository.findByRequestId(requestId)) {
            Set<Long> selectedItemIds = selectedItemIdsByOffer.get(offer.getId());
            if (selectedItemIds != null) {
                boolean allItemsSelected = offer.getItems().stream()
                        .allMatch(item -> selectedItemIds.contains(item.getId()));
                offer.setStatus(allItemsSelected ? OfferStatus.ACCEPTED : OfferStatus.PARTIALLY_ACCEPTED);
            } else if (offer.getStatus() == OfferStatus.PENDING) {
                offer.setStatus(OfferStatus.REJECTED);
            }
        }
    }

    private OrderSummaryResponse toSummary(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getPharmacy().getId(),
                order.getPharmacy().getName(),
                order.getItems().stream().map(OrderItem::getId).toList()
        );
    }

    private Customer requireCurrentCustomer() {
        User currentUser = currentUserProvider.get();
        if (!(currentUser instanceof Customer customer)) {
            throw new AccessDeniedException("Only customers can confirm medicine requests");
        }
        return customer;
    }
}
