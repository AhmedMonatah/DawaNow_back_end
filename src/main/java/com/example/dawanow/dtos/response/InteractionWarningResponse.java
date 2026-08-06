package com.example.dawanow.dtos.response;

import java.util.List;

/** One localized drug-interaction warning; severity is HIGH or MODERATE. */
public record InteractionWarningResponse(
        String severity,
        String title,
        String advice,
        List<InvolvedProductResponse> involvedProducts
) {
}
