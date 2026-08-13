package com.example.dawanow.entity;

public enum OrderStatus {
    PENDING,
    PENDING_PAYMENT,
    PREPARING,
    READY_FOR_PICKUP,
    READY_FOR_DELIVERY,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED
}
