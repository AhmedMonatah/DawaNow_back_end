package com.example.dawanow.dtos.request;

import java.time.LocalDate;

public record UpdateCustomerProfileRequest(
        String firstName,
        String lastName,
        String homeAddress,
        LocalDate dob,
        Double deliveryLatitude,
        Double deliveryLongitude
) {
}
