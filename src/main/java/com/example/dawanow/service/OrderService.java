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
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final PharmacyOfferRepository pharmacyOfferRepository;
    private final PharmacyRepository pharmacyRepository;
    private final CurrentUserProvider currentUserProvider;
    private final OrderMapper orderMapper;


    public OrderResponse createOrder(PharmacyOffer offer) {

        if (orderRepository.existsByOfferId(offer.getId())) {
            throw new IllegalArgumentException("An order already exists for this offer");
        }

        List<PharmacyOfferItem> acceptedItems = offer.getItems();

        if (acceptedItems.isEmpty()) {
            throw new IllegalArgumentException("The accepted offer does not contain any accepted items");
        }
        Order order = new Order();
        order.setUser(offer.getRequest().getCustomer());
        order.setPharmacy(offer.getPharmacy());
        order.setPharmacist(offer.getPharmacist());
        order.setOffer(offer);
        order.setDeliveryLatitude(offer.getRequest().getDeliveryLatitude());
        order.setDeliveryLongitude(offer.getRequest().getDeliveryLongitude());
        order.setDate(LocalDateTime.now());
        order.setDeliveryAddress(offer.getRequest().getDeliveryAddress());
        order.setTotalPrice(offer.getTotalPrice());

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

        if (!isApplicationAdmin(currentUser) && !isPharmacistAtPharmacy(currentUser, pharmacy)) {
            throw new AccessDeniedException(
                    "Only the pharmacy's pharmacist or a system administrator can view these orders"
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

        log.info("Current user: {} with role {}", currentUser.getId(), currentUser.getRole());
        log.info("Order user: {} with role {}", order.getUser().getId(), order.getUser().getRole());

        boolean ownsOrder = currentUser.getRole() == UserRole.CUSTOMER
                && order.getUser().getId().equals(currentUser.getId());
        log.info("{} == {} ? {}", order.getUser().getId(), currentUser.getId(), ownsOrder);
        boolean pharmacistCanView = isPharmacistAtPharmacy(currentUser, order.getPharmacy());
        if (!isApplicationAdmin(currentUser) && !ownsOrder && !pharmacistCanView) {
            log.warn("Access denied for user {} to view order {}", currentUser.getId(), order.getId());
            throw new AccessDeniedException("You are not allowed to view this order");
        }

        return orderMapper.toResponse(order);
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

    private boolean isPharmacistAtPharmacy(User user, Pharmacy pharmacy) {
        if (!(user instanceof Pharmacist pharmacist) || pharmacist.getPharmacy() == null) {
            return false;
        }

        return pharmacy.getId().equals(pharmacist.getPharmacy().getId());
    }

    private Customer requireCurrentCustomer() {
        User currentUser = currentUserProvider.get();
        if (!(currentUser instanceof Customer customer)) {
            throw new AccessDeniedException("Only customers can view their orders");
        }
        return customer;
    }
}
