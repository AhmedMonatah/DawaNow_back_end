package com.example.dawanow.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateOfferRequest(@Valid
                                   @NotEmpty
                                 List<CreateOfferItemRequest> items) {
}
