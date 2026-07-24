package com.example.dawanow.dtos.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ConfirmSelectionRequest(
        @NotEmpty List<Long> selectedItemIds
) {
}
