package com.example.dawanow.service;

import com.example.dawanow.dtos.request.CreateOrderRequest;
import com.example.dawanow.dtos.response.OrderResponse;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.entity.Customer;
import com.example.dawanow.entity.OfferItemStatus;
import com.example.dawanow.entity.OfferStatus;
import com.example.dawanow.entity.Order;
import com.example.dawanow.entity.OrderItem;
import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.entity.Pharmacy;
import com.example.dawanow.entity.PharmacyOffer;
import com.example.dawanow.entity.PharmacyOfferItem;
import com.example.dawanow.entity.Product;
import com.example.dawanow.entity.User;
import com.example.dawanow.entity.UserRole;
import com.example.dawanow.exception.ResourceNotFoundException;
import com.example.dawanow.mapper.OrderMapper;
import com.example.dawanow.repo.OrderRepository;
import com.example.dawanow.repo.PharmacyOfferRepository;
import com.example.dawanow.repo.PharmacyRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final PharmacyOfferRepository pharmacyOfferRepository;
    private final PharmacyRepository pharmacyRepository;
    private final CurrentUserProvider currentUserProvider;
    private final OrderMapper orderMapper;

    public OrderResponse createOrder(CreateOrderRequest request) {
        PharmacyOffer offer = pharmacyOfferRepository.findById(request.offerId())
                .orElseThrow(() -> new ResourceNotFoundException("Offer not found"));

        if (offer.getStatus() != OfferStatus.ACCEPTED
                && offer.getStatus() != OfferStatus.PARTIALLY_ACCEPTED) {
            throw new IllegalArgumentException("Only accepted offers can be used to create an order");
        }
        Pharmacist offerPharmacist = requireOfferPharmacist(offer);
        if (orderRepository.existsByOfferId(offer.getId())) {
            throw new IllegalArgumentException("An order already exists for this offer");
        }

        List<PharmacyOfferItem> acceptedItems = offer.getItems().stream()
                .filter(item -> item.getStatus() == OfferItemStatus.ACCEPTED)
                .toList();
        if (acceptedItems.isEmpty()) {
            throw new IllegalArgumentException("The accepted offer does not contain any accepted items");
        }

        Order order = new Order();
        order.setUser(offer.getRequest().getCustomer());
        order.setPharmacy(offer.getPharmacy());
        order.setPharmacist(offerPharmacist);
        order.setOffer(offer);
        order.setDeliveryLatitude(offer.getRequest().getDeliveryLatitude());
        order.setDeliveryLongitude(offer.getRequest().getDeliveryLongitude());
        order.setDate(LocalDate.now());

        BigDecimal totalPrice = BigDecimal.ZERO;
        for (PharmacyOfferItem offerItem : acceptedItems) {
            validateOfferItem(offerItem);
            Product product = offerItem.getRequestItem().getProduct();
            Long requestedQuantity = offerItem.getRequestItem().getQuantity();
            BigDecimal productPrice = product.getPrice();

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(requestedQuantity);
            orderItem.setUnitPrice(productPrice);
            order.getItems().add(orderItem);

            totalPrice = totalPrice.add(
                    productPrice.multiply(BigDecimal.valueOf(requestedQuantity))
            );
        }

        order.setTotalPrice(totalPrice);
        return orderMapper.toResponse(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<OrderResponse> getCurrentCustomerOrders(Pageable pageable) {
        Customer currentCustomer = requireCurrentCustomer();

        return PaginatedResponse.from(
                orderRepository.findByUserId(currentCustomer.getId(), pageable).map(orderMapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<OrderResponse> getPharmacyOrders(Long pharmacyId, Pageable pageable) {
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy not found"));
        User currentUser = currentUserProvider.get();

        if (!isApplicationAdmin(currentUser) && !isPharmacyAdmin(currentUser, pharmacy)) {
            throw new AccessDeniedException(
                    "Only the pharmacy's admin pharmacist or a system administrator can view these orders"
            );
        }

        return PaginatedResponse.from(
                orderRepository.findByPharmacyId(pharmacyId, pageable).map(orderMapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<OrderResponse> getAllOrders(Pageable pageable) {
        User currentUser = currentUserProvider.get();
        if (!isApplicationAdmin(currentUser)) {
            throw new AccessDeniedException("Only system administrators can view all orders");
        }

        return PaginatedResponse.from(orderRepository.findAll(pageable).map(orderMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        User currentUser = currentUserProvider.get();

        boolean ownsOrder = currentUser instanceof Customer
                && order.getUser().getId().equals(currentUser.getId());
        boolean administersPharmacy = isPharmacyAdmin(currentUser, order.getPharmacy());
        if (!isApplicationAdmin(currentUser) && !ownsOrder && !administersPharmacy) {
            throw new AccessDeniedException("You are not allowed to view this order");
        }

        return orderMapper.toResponse(order);
    }

    private Pharmacist requireOfferPharmacist(PharmacyOffer offer) {
        Pharmacist pharmacist = offer.getPharmacist();
        if (pharmacist == null) {
            throw new IllegalArgumentException("The offer must have a pharmacist before creating an order");
        }
        if (pharmacist.getPharmacy() == null
                || !pharmacist.getPharmacy().getId().equals(offer.getPharmacy().getId())) {
            throw new IllegalArgumentException("The offer pharmacist must belong to the offer's pharmacy");
        }
        return pharmacist;
    }

    private void validateOfferItem(PharmacyOfferItem offerItem) {
        if (offerItem.getRequestItem().getQuantity() == null || offerItem.getRequestItem().getQuantity() <= 0) {
            throw new IllegalArgumentException("Requested quantity must be positive");
        }
        if (offerItem.getRequestItem().getProduct().getPrice() == null
                || offerItem.getRequestItem().getProduct().getPrice().signum() <= 0) {
            throw new IllegalArgumentException("Product price must be positive");
        }
    }

    private boolean isApplicationAdmin(User user) {
        return user.getRole() == UserRole.ADMIN;
    }

    private boolean isPharmacyAdmin(User user, Pharmacy pharmacy) {
        if (!(user instanceof Pharmacist pharmacist) || pharmacist.getPharmacy() == null) {
            return false;
        }

        return pharmacy.getId().equals(pharmacist.getPharmacy().getId())
                && pharmacy.getAdminPharmacist().getId().equals(pharmacist.getId());
    }

    private Customer requireCurrentCustomer() {
        User currentUser = currentUserProvider.get();
        if (!(currentUser instanceof Customer customer)) {
            throw new AccessDeniedException("Only customers can view their orders");
        }
        return customer;
    }
}
