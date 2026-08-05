package com.example.dawanow.service.ai.chat;

import com.example.dawanow.entity.MedicationReminder;
import com.example.dawanow.entity.User;
import com.example.dawanow.repo.MedicationReminderRepository;
import com.example.dawanow.service.ai.chat.AiChatModelClient.ReminderSpec;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates, lists and cancels medication reminders on behalf of the chat.
 * All times are interpreted in Africa/Cairo.
 */
@Service
@RequiredArgsConstructor
public class MedicationReminderService {

    public static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int DEFAULT_DURATION_DAYS = 7;
    private static final int MAX_DURATION_DAYS = 90;
    private static final int MAX_TIMES_PER_DAY = 4;

    private final MedicationReminderRepository repository;

    public enum DeletionStatus { DELETED, AMBIGUOUS, NOT_FOUND }

    public record DeletionResult(DeletionStatus status, List<String> names) {
    }

    @Transactional
    public MedicationReminder create(User user, ReminderSpec spec) {
        MedicationReminder reminder = new MedicationReminder();
        reminder.setUser(user);
        reminder.setMedicineName(spec.medicine().trim());
        reminder.setTimesCsv(String.join(",", resolveTimes(spec)));
        reminder.setDurationDays(clampDuration(spec.durationDays()));
        reminder.setStartDate(LocalDate.now(CAIRO));
        reminder.setActive(true);
        return repository.save(reminder);
    }

    /** Active reminders, lazily deactivating the ones whose course has ended. */
    @Transactional
    public List<MedicationReminder> list(User user) {
        LocalDate today = LocalDate.now(CAIRO);
        List<MedicationReminder> active = new ArrayList<>();
        for (MedicationReminder reminder : repository.findByUserIdAndActiveTrueOrderByIdAsc(user.getId())) {
            if (isExpired(reminder, today)) {
                reminder.setActive(false);
                repository.save(reminder);
            } else {
                active.add(reminder);
            }
        }
        return active;
    }

    /**
     * Cancels a reminder by (fuzzy) name among THIS user's active reminders:
     * exact normalized match first, then a single containment match; several
     * containment matches come back as AMBIGUOUS so the chat can ask.
     */
    @Transactional
    public DeletionResult deactivateByName(User user, String name) {
        String wanted = normalize(name);
        if (wanted.isEmpty()) {
            return new DeletionResult(DeletionStatus.NOT_FOUND, List.of());
        }
        List<MedicationReminder> reminders = list(user);

        List<MedicationReminder> exact = reminders.stream()
                .filter(reminder -> normalize(reminder.getMedicineName()).equals(wanted))
                .toList();
        if (!exact.isEmpty()) {
            return deactivate(exact);
        }

        List<MedicationReminder> partial = reminders.stream()
                .filter(reminder -> {
                    String existing = normalize(reminder.getMedicineName());
                    return existing.contains(wanted) || wanted.contains(existing);
                })
                .toList();
        if (partial.isEmpty()) {
            return new DeletionResult(DeletionStatus.NOT_FOUND, List.of());
        }
        if (partial.size() > 1) {
            return new DeletionResult(
                    DeletionStatus.AMBIGUOUS,
                    partial.stream().map(MedicationReminder::getMedicineName).toList()
            );
        }
        return deactivate(partial);
    }

    public List<String> times(MedicationReminder reminder) {
        return List.of(reminder.getTimesCsv().split(","));
    }

    static boolean isExpired(MedicationReminder reminder, LocalDate today) {
        return reminder.getStartDate().plusDays(reminder.getDurationDays()).isBefore(today.plusDays(1));
    }

    private DeletionResult deactivate(List<MedicationReminder> reminders) {
        for (MedicationReminder reminder : reminders) {
            reminder.setActive(false);
            repository.save(reminder);
        }
        return new DeletionResult(
                DeletionStatus.DELETED,
                reminders.stream().map(MedicationReminder::getMedicineName).toList()
        );
    }

    /**
     * Explicit clock times win; otherwise sensible defaults per times-per-day.
     * Unparseable entries are dropped rather than failing the whole request.
     */
    private TreeSet<String> resolveTimes(ReminderSpec spec) {
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
            return times;
        }

        int perDay = spec.timesPerDay() == null ? 0 : spec.timesPerDay();
        List<String> defaults = switch (perDay) {
            case 1 -> List.of("09:00");
            case 3 -> List.of("09:00", "15:00", "21:00");
            case 4 -> List.of("06:00", "12:00", "18:00", "22:00");
            default -> List.of("09:00", "21:00");
        };
        times.addAll(defaults);
        return times;
    }

    private java.util.Optional<LocalTime> parseTime(String value) {
        if (value == null || value.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(LocalTime.parse(value.trim(), TIME_FORMAT));
        } catch (DateTimeParseException exception) {
            return java.util.Optional.empty();
        }
    }

    private int clampDuration(Integer requested) {
        if (requested == null || requested < 1) {
            return DEFAULT_DURATION_DAYS;
        }
        return Math.min(requested, MAX_DURATION_DAYS);
    }

    /**
     * Lowercase, letters and digits only. Digits are kept on purpose: "Concor"
     * must not be an EXACT match for "Concor 5" when "Concor Cor" also exists —
     * that case should surface as ambiguous, not silently delete one of them.
     */
    private String normalize(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
