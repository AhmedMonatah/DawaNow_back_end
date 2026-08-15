package com.example.dawanow.service.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatSafetyGuardTest {

    private final ChatSafetyGuard guard = new ChatSafetyGuard();

    @Test
    void flagsEnglishCardiacAndBreathingComplaints() {
        assertThat(guard.hasRedFlag("I have chest pain when I walk")).isTrue();
        assertThat(guard.hasRedFlag("my father can't breathe")).isTrue();
        assertThat(guard.hasRedFlag("she fainted this morning")).isTrue();
    }

    @Test
    void flagsArabicComplaintsRegardlessOfHamzaSpelling() {
        assertThat(guard.hasRedFlag("عندي ألم في الصدر")).isTrue();
        assertThat(guard.hasRedFlag("عندي الم في الصدر من امبارح")).isTrue();
        assertThat(guard.hasRedFlag("مش قادر أتنفس")).isTrue();
        assertThat(guard.hasRedFlag("عندي نزيف شديد")).isTrue();
    }

    @Test
    void doesNotFlagMinorEverydaySymptoms() {
        assertThat(guard.hasRedFlag("اسمي محمود وعندي صداع")).isFalse();
        assertThat(guard.hasRedFlag("I have a mild headache")).isFalse();
        assertThat(guard.hasRedFlag("do you have Panadol?")).isFalse();
        assertThat(guard.hasRedFlag("عندي برد وكحة خفيفة")).isFalse();
    }

    @Test
    void doesNotFlagEverydayHeartIdioms() {
        assertThat(guard.hasRedFlag("شكرا من قلبي")).isFalse();
        assertThat(guard.hasRedFlag("thank you from the bottom of my heart")).isFalse();
    }

    @Test
    void handlesBlankInput() {
        assertThat(guard.hasRedFlag(null)).isFalse();
        assertThat(guard.hasRedFlag("   ")).isFalse();
    }
}
