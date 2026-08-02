package com.example.dawanow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.dawanow.dtos.response.OrderGroupResponse;
import com.example.dawanow.dtos.response.OrderItemResponse;
import com.example.dawanow.dtos.response.OrderResponse;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.entity.Customer;
import com.example.dawanow.entity.MedicineRequest;
import com.example.dawanow.entity.Order;
import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.entity.Pharmacy;
import com.example.dawanow.entity.User;
import com.example.dawanow.mapper.OrderMapper;
import com.example.dawanow.repo.OrderItemRepository;
import com.example.dawanow.repo.OrderRepository;
import com.example.dawanow.repo.PharmacyOfferRepository;
import com.example.dawanow.repo.PharmacyRepository;
import com.example.dawanow.repo.ProductRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private PharmacyOfferRepository pharmacyOfferRepository;
    @Mock
    private PharmacyRepository pharmacyRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private OrderMapper orderMapper;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(
                orderRepository, orderItemRepository, productRepository,
                pharmacyOfferRepository, pharmacyRepository,
                currentUserProvider, orderMapper
        );
    }

    @Test
    void groupsOrdersByRequestIdPreservingOrder() {
        Customer customer = customer(1L);
        when(currentUserProvider.get()).thenReturn(customer);

        PageRequest pageable = PageRequest.of(0, 20);

        List<Long> requestIds = List.of(30L, 20L);
        when(orderRepository.findRequestIdsByUserId(1L, pageable)).thenReturn(requestIds);
        when(orderRepository.countDistinctRequestIdsByUserId(1L)).thenReturn(2L);

        Order order1 = orderWithRequestId(1L, 30L);
        Order order2 = orderWithRequestId(2L, 30L);
        Order order3 = orderWithRequestId(3L, 20L);
        List<Order> orders = List.of(order1, order2, order3);
        when(orderRepository.findByUserIdAndRequestIdIn(1L, requestIds)).thenReturn(orders);

        List<OrderItemResponse> noItems = List.of();
        when(orderItemRepository.findByOrderIdIn(List.of(1L, 2L, 3L))).thenReturn(noItems);

        OrderResponse r1 = orderResponse(1L);
        OrderResponse r2 = orderResponse(2L);
        OrderResponse r3 = orderResponse(3L);
        when(orderMapper.toResponse(order1, noItems)).thenReturn(r1);
        when(orderMapper.toResponse(order2, noItems)).thenReturn(r2);
        when(orderMapper.toResponse(order3, noItems)).thenReturn(r3);

        PaginatedResponse<OrderGroupResponse> result = service.getCurrentCustomerOrders("en", pageable);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).requestId()).isEqualTo(30L);
        assertThat(result.content().get(0).orders()).containsExactly(r1, r2);
        assertThat(result.content().get(1).requestId()).isEqualTo(20L);
        assertThat(result.content().get(1).orders()).containsExactly(r3);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.last()).isTrue();
    }

    @Test
    void returnsEmptyWhenNoOrders() {
        Customer customer = customer(1L);
        when(currentUserProvider.get()).thenReturn(customer);

        PageRequest pageable = PageRequest.of(0, 20);
        when(orderRepository.findRequestIdsByUserId(1L, pageable)).thenReturn(List.of());

        PaginatedResponse<OrderGroupResponse> result = service.getCurrentCustomerOrders("en", pageable);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    void throwsWhenUserIsNotCustomer() {
        Pharmacist pharmacist = new Pharmacist();
        pharmacist.setId(2L);
        when(currentUserProvider.get()).thenReturn(pharmacist);

        assertThatThrownBy(() -> service.getCurrentCustomerOrders("en", PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only customers can view their orders");
    }

    private static Customer customer(Long id) {
        Customer c = new Customer();
        c.setId(id);
        return c;
    }

    private static Order orderWithRequestId(Long orderId, Long requestId) {
        Order o = new Order();
        o.setId(orderId);
        MedicineRequest r = new MedicineRequest();
        r.setId(requestId);
        o.setRequest(r);
        return o;
    }

    private static OrderResponse orderResponse(Long id) {
        return new OrderResponse(
                id, 1L, "name", "notes", "addr", "phone", null,
                1L, "pharm", "pharmAddr", "pharmPhone",
                1L, "pharmacist", 1L, null, null, null,
                null, null, LocalDate.now(), null, null, null, null, List.of()
        );
    }
}
