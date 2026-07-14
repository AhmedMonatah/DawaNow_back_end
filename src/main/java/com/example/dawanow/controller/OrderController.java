package com.example.dawanow.controller;

import com.example.dawanow.dtos.request.CreateOrderRequest;
import com.example.dawanow.dtos.response.ApiResponse;
import com.example.dawanow.dtos.response.OrderResponse;
import com.example.dawanow.dtos.response.PaginatedResponse;
import com.example.dawanow.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("hasRole('PHARMACIST')")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Order created", orderService.createOrder(request)));
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<PaginatedResponse<OrderResponse>>> getCurrentCustomerOrders(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("Orders fetched", orderService.getCurrentCustomerOrders(pageable)));
    }

    //check that the pharmacist is in the pharmacy in service layer
    @GetMapping("/pharmacy/{pharmacyId}")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<OrderResponse>>> getPharmacyOrders(
            @PathVariable Long pharmacyId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("Pharmacy orders fetched", orderService.getPharmacyOrders(pharmacyId, pageable)));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<OrderResponse>>> getAllOrders(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("Orders fetched", orderService.getAllOrders(pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Order fetched", orderService.getOrderById(id)));
    }
}
