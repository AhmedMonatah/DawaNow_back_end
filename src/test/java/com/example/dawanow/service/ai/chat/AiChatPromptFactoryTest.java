package com.example.dawanow.service.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dawanow.dtos.response.PharmacistPerformanceEntryResponse;
import com.example.dawanow.dtos.response.PharmacistRankingResponse;
import com.example.dawanow.entity.ChatPerformanceDirection;
import com.example.dawanow.entity.DashboardPeriod;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiChatPromptFactoryTest {

    private final AiChatPromptFactory promptFactory = new AiChatPromptFactory();

    @Test
    void bottomPerformanceReplyUsesEnglishLeastLabels() {
        String reply = promptFactory.pharmacistPerformanceReply(
                "en",
                DashboardPeriod.LAST_WEEK,
                ChatPerformanceDirection.BOTTOM,
                List.of(ranking("SUCCESSFUL_ORDERS"))
        );

        assertThat(reply)
                .contains("lowest-performing")
                .contains("Fewest successful orders")
                .contains("Mona Ali");
    }

    @Test
    void bottomPerformanceReplyUsesArabicLeastLabels() {
        String reply = promptFactory.pharmacistPerformanceReply(
                "ar",
                DashboardPeriod.LAST_WEEK,
                ChatPerformanceDirection.BOTTOM,
                List.of(ranking("OFFERS_CREATED"))
        );

        assertThat(reply)
                .contains("الأقل أداءً")
                .contains("الأقل إنشاءً للعروض")
                .contains("Mona Ali");
    }

    private PharmacistRankingResponse ranking(String metric) {
        return new PharmacistRankingResponse(
                metric,
                "LAST_WEEK",
                "BOTTOM",
                List.of(new PharmacistPerformanceEntryResponse(1, 7L, "Mona", "Ali", 0))
        );
    }
}
