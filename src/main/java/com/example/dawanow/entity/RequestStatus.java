package com.example.dawanow.entity;

public enum RequestStatus {
    SEARCHING,      // Waiting for pharmacy offers
    OFFERS_READY,   // Search ended and offers are available
    PENDING, // Customer accepted one offer
    COMPLETED,      // Order delivered
    CANCELLED,
    EXPIRED,         // No offers received before timeout
}