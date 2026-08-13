package com.example.dawanow.entity;

public enum RequestStatus {
    SEARCHING,      // Open for offers, waiting for the customer to confirm a selection
    COMPLETED,      // Customer confirmed a selection; request is done, never touched again
    CANCELLED,
    EXPIRED,         // No selection confirmed before the search timeout
}