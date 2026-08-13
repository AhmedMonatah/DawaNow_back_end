package com.example.dawanow.dtos.response;

import java.util.List;

public record ReminderResponse(
        String medicineName,
        List<String> times,
        int durationDays
) {
}
