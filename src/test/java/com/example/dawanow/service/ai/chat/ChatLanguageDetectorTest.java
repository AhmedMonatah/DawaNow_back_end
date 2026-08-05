package com.example.dawanow.service.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatLanguageDetectorTest {

    private final ChatLanguageDetector detector = new ChatLanguageDetector();

    @Test
    void detectsEgyptianArabicAsArabic() {
        assertThat(detector.detect("عايز حاجة للصداع")).isEqualTo("ar");
    }

    @Test
    void detectsEnglishAsEnglish() {
        assertThat(detector.detect("I need something for a headache")).isEqualTo("en");
    }

    @Test
    void mixedTextWithAnyArabicIsArabic() {
        assertThat(detector.detect("do you have بنادول ?")).isEqualTo("ar");
    }

    @Test
    void blankAndNullDefaultToEnglish() {
        assertThat(detector.detect(null)).isEqualTo("en");
        assertThat(detector.detect("  ")).isEqualTo("en");
    }
}
