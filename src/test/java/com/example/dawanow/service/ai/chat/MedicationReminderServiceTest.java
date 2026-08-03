package com.example.dawanow.service.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.dawanow.entity.MedicationReminder;
import com.example.dawanow.entity.User;
import com.example.dawanow.repo.MedicationReminderRepository;
import com.example.dawanow.service.ai.chat.AiChatModelClient.ReminderSpec;
import com.example.dawanow.service.ai.chat.MedicationReminderService.DeletionResult;
import com.example.dawanow.service.ai.chat.MedicationReminderService.DeletionStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MedicationReminderServiceTest {

    @Mock
    private MedicationReminderRepository repository;

    private MedicationReminderService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new MedicationReminderService(repository);
        user = new User();
        user.setId(1L);
    }

    @Test
    void explicitTimesWinOverDefaults() {
        stubSave();

        MedicationReminder reminder = service.create(user,
                new ReminderSpec("Concor", 3, List.of("08:30", "20:15"), 10));

        assertThat(reminder.getTimesCsv()).isEqualTo("08:30,20:15");
        assertThat(reminder.getDurationDays()).isEqualTo(10);
    }

    @Test
    void timesPerDayMapsToSensibleDefaults() {
        stubSave();

        assertThat(service.create(user, new ReminderSpec("A", 1, List.of(), null)).getTimesCsv())
                .isEqualTo("09:00");
        assertThat(service.create(user, new ReminderSpec("B", 3, List.of(), null)).getTimesCsv())
                .isEqualTo("09:00,15:00,21:00");
        // Unstated frequency falls back to morning + evening.
        assertThat(service.create(user, new ReminderSpec("C", null, List.of(), null)).getTimesCsv())
                .isEqualTo("09:00,21:00");
    }

    @Test
    void invalidTimesAreDroppedAndDurationClamped() {
        stubSave();

        MedicationReminder reminder = service.create(user,
                new ReminderSpec("X", null, List.of("nonsense", "25:99"), 500));

        assertThat(reminder.getTimesCsv()).isEqualTo("09:00,21:00");
        assertThat(reminder.getDurationDays()).isEqualTo(90);
    }

    @Test
    void deleteMatchesExactNameFirst() {
        stubSave();
        when(repository.findByUserIdAndActiveTrueOrderByIdAsc(1L))
                .thenReturn(List.of(reminder("Concor"), reminder("Concor Cor")));

        DeletionResult result = service.deactivateByName(user, "concor");

        assertThat(result.status()).isEqualTo(DeletionStatus.DELETED);
        assertThat(result.names()).containsExactly("Concor");
    }

    @Test
    void deleteWithMultiplePartialMatchesAsksWhichOne() {
        when(repository.findByUserIdAndActiveTrueOrderByIdAsc(1L))
                .thenReturn(List.of(reminder("Concor 5"), reminder("Concor Cor 2.5")));

        DeletionResult result = service.deactivateByName(user, "كونكور");

        // Arabic name doesn't match the Latin ones at all -> NOT_FOUND, while
        // a Latin partial like "concor" is ambiguous between the two.
        assertThat(result.status()).isEqualTo(DeletionStatus.NOT_FOUND);
        assertThat(service.deactivateByName(user, "concor").status())
                .isEqualTo(DeletionStatus.AMBIGUOUS);
    }

    @Test
    void listDeactivatesExpiredReminders() {
        stubSave();
        MedicationReminder expired = reminder("Old");
        expired.setStartDate(LocalDate.now(MedicationReminderService.CAIRO).minusDays(30));
        expired.setDurationDays(7);
        MedicationReminder current = reminder("Fresh");
        when(repository.findByUserIdAndActiveTrueOrderByIdAsc(1L))
                .thenReturn(List.of(expired, current));

        List<MedicationReminder> active = service.list(user);

        assertThat(active).containsExactly(current);
        assertThat(expired.isActive()).isFalse();
    }

    private MedicationReminder reminder(String name) {
        MedicationReminder reminder = new MedicationReminder();
        reminder.setUser(user);
        reminder.setMedicineName(name);
        reminder.setTimesCsv("09:00,21:00");
        reminder.setDurationDays(7);
        reminder.setStartDate(LocalDate.now(MedicationReminderService.CAIRO));
        reminder.setActive(true);
        return reminder;
    }

    private void stubSave() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
