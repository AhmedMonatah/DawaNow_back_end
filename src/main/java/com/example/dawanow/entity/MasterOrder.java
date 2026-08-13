package com.example.dawanow.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "master_orders")
@Getter
@Setter
@NoArgsConstructor
public class MasterOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(
            mappedBy = "masterOrder",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<Order> orders = new ArrayList<>();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medicine_request_id", nullable = false, unique = true)
    private MedicineRequest request;

    @Column(name = "payment_intent_id", unique = true, length = 255)
    private String paymentIntentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 32)
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    /** Used only for CARD (Stripe). Null for CASH — cash is not tracked. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 32)
    private PaymentStatus paymentStatus;

    private LocalDateTime paymentExpiresAt;

    /** Used only for CARD. Null for CASH. */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    private FulfillmentMethod fulfillmentMethod;

    @Column(name = "delivery_fee", precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column
    OrderStatus orderStatus = OrderStatus.PENDING;

    public void addCustomerOrder(Order order) {
        this.orders.add(order);
        order.setMasterOrder(this);
    }

    public void applyOrderStatus(OrderStatus status) {
        this.orderStatus = status;
        this.orders.forEach(order -> order.setStatus(status));
    }

}
