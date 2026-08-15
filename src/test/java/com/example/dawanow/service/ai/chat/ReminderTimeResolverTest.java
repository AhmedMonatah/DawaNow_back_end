package com.example.dawanow.service.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dawanow.service.ai.chat.AiChatModelClient.ReminderSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReminderTimeResolverTest {

    private final ReminderTimeResolver resolver = new ReminderTimeResolver();

    @Test
    void explicitTimesWinOverDefaults() {
        List<String> times = resolver.resolveTimes(
                new ReminderSpec("Concor", 3, List.of("08:30", "20:15"), null));

        assertThat(times).containsExactly("08:30", "20:15");
    }

    @Test
    void timesPerDayMapsToSensibleDefaults() {
        assertThat(resolver.resolveTimes(new ReminderSpec("A", 1, List.of(), null)))
                .containsExactly("09:00");
        assertThat(resolver.resolveTimes(new ReminderSpec("B", 3, List.of(), null)))
                .containsExactly("09:00", "15:00", "21:00");
        assertThat(resolver.resolveTimes(new ReminderSpec("C", 4, List.of(), null)))
                .containsExactly("06:00", "12:00", "18:00", "22:00");
        // Unstated frequency falls back to morning + evening.
        assertThat(resolver.resolveTimes(new ReminderSpec("D", null, List.of(), null)))
                .containsExactly("09:00", "21:00");
    }

    @Test
    void invalidTimesAreDroppedAndFallBackToDefaults() {
        List<String> times = resolver.resolveTimes(
                new ReminderSpec("X", null, List.of("nonsense", "25:99"), null));

        assertThat(times).containsExactly("09:00", "21:00");
    }

    @Test
    void durationDefaultsAndClamps() {
        assertThat(resolver.clampDuration(null)).isEqualTo(7);
        assertThat(resolver.clampDuration(0)).isEqualTo(7);
        assertThat(resolver.clampDuration(-1)).isEqualTo(7);
        assertThat(resolver.clampDuration(30)).isEqualTo(30);
        assertThat(resolver.clampDuration(500)).isEqualTo(90);
    }
}
