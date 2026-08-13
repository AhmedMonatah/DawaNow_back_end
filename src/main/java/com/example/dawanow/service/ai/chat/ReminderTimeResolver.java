package com.example.dawanow.service.ai.chat;

import com.example.dawanow.service.ai.chat.AiChatModelClient.ReminderSpec;

import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

@Component
public class ReminderTimeResolver {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int DEFAULT_DURATION_DAYS = 7;
    private static final int MAX_DURATION_DAYS = 90;
    private static final int MAX_TIMES_PER_DAY = 4;

    public List<String> resolveTimes(ReminderSpec spec) {
        TreeSet<String> times = new TreeSet<>();
        if (spec.times() != null) {
            for (String time : spec.times()) {
                parseTime(time).ifPresent(parsed -> times.add(parsed.format(TIME_FORMAT)));
                if (times.size() == MAX_TIMES_PER_DAY) {
                    break;
                }
            }
        }
        if (!times.isEmpty()) {
            return List.copyOf(times);
        }

        int perDay = spec.timesPerDay() == null ? 0 : spec.timesPerDay();
        List<String> defaults = switch (perDay) {
            case 1 -> List.of("09:00");
            case 3 -> List.of("09:00", "15:00", "21:00");
            case 4 -> List.of("06:00", "12:00", "18:00", "22:00");
            default -> List.of("09:00", "21:00");
        };
        times.addAll(defaults);
        return List.copyOf(times);
    }

    public int clampDuration(Integer requested) {
        if (requested == null || requested < 1) {
            return DEFAULT_DURATION_DAYS;
        }
        return Math.min(requested, MAX_DURATION_DAYS);
    }

    private Optional<LocalTime> parseTime(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalTime.parse(value.trim(), TIME_FORMAT));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }
}
