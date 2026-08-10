package com.example.dawanow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.dawanow.dtos.response.MasterOrderResponse;
import com.example.dawanow.dtos.response.OrderItemResponse;
import com.example.dawanow.dtos.response.OrderResponse;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.entity.Customer;
import com.example.dawanow.entity.MasterOrder;
import com.example.dawanow.entity.MedicineRequest;
import com.example.dawanow.entity.Order;
import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.mapper.MasterOrderMapper;
import com.example.dawanow.mapper.OrderMapper;
import com.example.dawanow.repo.MasterOrderRepository;
import com.example.dawanow.repo.OrderItemRepository;
import com.example.dawanow.repo.OrderRepository;
import com.example.dawanow.repo.PharmacyRepository;
import com.example.dawanow.repo.ProductRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
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
    private PharmacyRepository pharmacyRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private MasterOrderRepository masterOrderRepository;
    @Mock
    private MasterOrderMapper masterOrderMapper;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(
                orderRepository, orderItemRepository, productRepository,
                pharmacyRepository, currentUserProvider, orderMapper,
                masterOrderRepository, masterOrderMapper
        );
    }

    @Test
    void returnsMasterOrdersWithResolvedSubOrders() {
        Customer customer = customer(1L);
        when(currentUserProvider.get()).thenReturn(customer);

        MasterOrder masterOrder = new MasterOrder();
        masterOrder.setId(100L);
        MedicineRequest request = new MedicineRequest();
        request.setId(7L);
        masterOrder.setRequest(request);

        Order order1 = order(1L);
        Order order2 = order(2L);
        masterOrder.addCustomerOrder(order1);
        masterOrder.addCustomerOrder(order2);

        PageRequest pageable = PageRequest.of(0, 20);
        when(masterOrderRepository.findByUserId(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(masterOrder), pageable, 1));
        when(orderItemRepository.findByOrderIdIn(List.of(1L, 2L))).thenReturn(List.of());

        OrderResponse r1 = orderResponse(1L);
        OrderResponse r2 = orderResponse(2L);
        when(orderMapper.toResponse(order1, List.of())).thenReturn(r1);
        when(orderMapper.toResponse(order2, List.of())).thenReturn(r2);

        MasterOrderResponse masterResponse = new MasterOrderResponse(
                100L, 7L, List.of(r1, r2), null, null, null,
                new BigDecimal("25"), new BigDecimal("50"), null, null, null);
        when(masterOrderMapper.toResponse(masterOrder, List.of(r1, r2)))
                .thenReturn(masterResponse);

        PaginatedResponse<MasterOrderResponse> result =
                service.getCurrentCustomerMasterOrders("en", pageable);

        assertThat(result.content()).containsExactly(masterResponse);
    }

    @Test
    void returnsEmptyWhenCustomerHasNoMasterOrders() {
        Customer customer = customer(1L);
        when(currentUserProvider.get()).thenReturn(customer);

        PageRequest pageable = PageRequest.of(0, 20);
        when(masterOrderRepository.findByUserId(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PaginatedResponse<MasterOrderResponse> result =
                service.getCurrentCustomerMasterOrders("en", pageable);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    void throwsWhenUserIsNotCustomer() {
        Pharmacist pharmacist = new Pharmacist();
        pharmacist.setId(2L);
        when(currentUserProvider.get()).thenReturn(pharmacist);

        assertThatThrownBy(() -> service.getCurrentCustomerMasterOrders("en", PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only customers can view their orders");
    }

    private static Customer customer(Long id) {
        Customer c = new Customer();
        c.setId(id);
        return c;
    }

    private static Order order(Long orderId) {
        Order o = new Order();
        o.setId(orderId);
        return o;
    }

    private static OrderResponse orderResponse(Long id) {
        return new OrderResponse(
                id, 1L, "name", "notes", "addr", "phone", null,
                1L, "pharm", "pharmAddr", "pharmPhone",
                1L, "pharmacist", 1L, null, null,
                null, null, LocalDate.now(), null, null, null, null, List.of()
        );
    }
}