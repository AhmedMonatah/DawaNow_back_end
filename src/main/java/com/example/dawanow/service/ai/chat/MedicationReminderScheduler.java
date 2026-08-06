package com.example.dawanow.service.ai.chat;

import com.example.dawanow.entity.MedicationReminder;
import com.example.dawanow.repo.MedicationReminderRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fires due medication reminders once per configured time slot.
 *
 * <p>{@code lastNotifiedAt} is written BEFORE the (currently empty) push call,
 * so a slot never double-fires even across quick restarts within the same
 * minute.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MedicationReminderScheduler {

    private final MedicationReminderRepository repository;

    @Scheduled(cron = "0 * * * * *", zone = "Africa/Cairo")
    @Transactional
    public void dispatchDueReminders() {
        dispatch(LocalDateTime.now(MedicationReminderService.CAIRO));
    }

    void dispatch(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalTime currentMinute = now.toLocalTime().withSecond(0).withNano(0);

        for (MedicationReminder reminder : repository.findByActiveTrue()) {
            if (MedicationReminderService.isExpired(reminder, today)) {
                reminder.setActive(false);
                repository.save(reminder);
                continue;
            }
            for (String slot : reminder.getTimesCsv().split(",")) {
                if (!slot.trim().equals(currentMinute.toString())) {
                    continue;
                }
                LocalDateTime slotMoment = today.atTime(LocalTime.parse(slot.trim()));
                if (reminder.getLastNotifiedAt() != null
                        && !reminder.getLastNotifiedAt().isBefore(slotMoment)) {
                    continue;
                }
                reminder.setLastNotifiedAt(now);
                repository.save(reminder);
                sendReminderPush(reminder);
                break;
            }
        }
    }

    /**
     * TODO: customer push notifications are not implemented yet — DeviceToken is
     * pharmacist-only today. When customer device-token support lands in the
     * customers app, resolve the user's tokens here and send the reminder via
     * FCM ("Time to take {medicineName}"). Intentionally left empty until then;
     * the scheduler already records the slot in lastNotifiedAt.
     */
    void sendReminderPush(MedicationReminder reminder) {
        log.info("Reminder due for user {} medicine '{}' (push not sent: customer push TODO)",
                reminder.getUser().getId(), reminder.getMedicineName());
    }
}
