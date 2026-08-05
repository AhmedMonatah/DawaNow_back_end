package com.example.dawanow.service.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.dawanow.entity.MedicationReminder;
import com.example.dawanow.entity.User;
import com.example.dawanow.repo.MedicationReminderRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MedicationReminderSchedulerTest {

    @Mock
    private MedicationReminderRepository repository;

    private MedicationReminderScheduler scheduler;
    private final LocalDate today = LocalDate.of(2026, 8, 3);

    @BeforeEach
    void setUp() {
        scheduler = new MedicationReminderScheduler(repository);
        org.mockito.Mockito.lenient()
                .when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void dueSlotFiresOncePerMinuteEvenAcrossRepeatedRuns() {
        MedicationReminder reminder = reminder("09:00,21:00");
        when(repository.findByActiveTrue()).thenReturn(List.of(reminder));
        LocalDateTime nineAm = today.atTime(LocalTime.of(9, 0, 30));

        scheduler.dispatch(nineAm);
        assertThat(reminder.getLastNotifiedAt()).isEqualTo(nineAm);

        // Second sweep in the same minute must not re-fire the slot.
        LocalDateTime stillNine = today.atTime(LocalTime.of(9, 0, 59));
        scheduler.dispatch(stillNine);
        assertThat(reminder.getLastNotifiedAt()).isEqualTo(nineAm);
    }

    @Test
    void eveningSlotFiresAfterMorningSlotSameDay() {
        MedicationReminder reminder = reminder("09:00,21:00");
        when(repository.findByActiveTrue()).thenReturn(List.of(reminder));

        scheduler.dispatch(today.atTime(9, 0));
        scheduler.dispatch(today.atTime(21, 0));

        assertThat(reminder.getLastNotifiedAt()).isEqualTo(today.atTime(21, 0));
    }

    @Test
    void offSlotMinutesDoNothing() {
        MedicationReminder reminder = reminder("09:00");
        when(repository.findByActiveTrue()).thenReturn(List.of(reminder));

        scheduler.dispatch(today.atTime(9, 1));

        assertThat(reminder.getLastNotifiedAt()).isNull();
    }

    @Test
    void expiredReminderIsDeactivatedInsteadOfFiring() {
        MedicationReminder reminder = reminder("09:00");
        reminder.setStartDate(today.minusDays(10));
        reminder.setDurationDays(3);
        when(repository.findByActiveTrue()).thenReturn(List.of(reminder));

        scheduler.dispatch(today.atTime(9, 0));

        assertThat(reminder.isActive()).isFalse();
        assertThat(reminder.getLastNotifiedAt()).isNull();
    }

    private MedicationReminder reminder(String timesCsv) {
        User user = new User();
        user.setId(1L);
        MedicationReminder reminder = new MedicationReminder();
        reminder.setUser(user);
        reminder.setMedicineName("Concor");
        reminder.setTimesCsv(timesCsv);
        reminder.setDurationDays(7);
        reminder.setStartDate(today);
        reminder.setActive(true);
        return reminder;
    }
}
