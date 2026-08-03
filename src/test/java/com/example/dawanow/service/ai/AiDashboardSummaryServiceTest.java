package com.example.dawanow.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.dawanow.config.AiChatProperties;
import com.example.dawanow.dtos.response.AiDashboardSummaryResponse;
import com.example.dawanow.dtos.response.MostSoldProductResponse;
import com.example.dawanow.dtos.response.PharmacyDashboardResponse;
import com.example.dawanow.entity.DashboardPeriod;
import com.example.dawanow.entity.User;
import com.example.dawanow.service.CurrentUserProvider;
import com.example.dawanow.service.PharmacyDashboardService;
import com.example.dawanow.service.ai.chat.AiChatModelClient;
import com.example.dawanow.service.ai.chat.AiChatPromptFactory;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class AiDashboardSummaryServiceTest {

    @Mock
    private PharmacyDashboardService dashboardService;
    @Mock
    private AiChatModelClient modelClient;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private AiDashboardSummaryService service;

    @BeforeEach
    void setUp() {
        service = new AiDashboardSummaryService(
                dashboardService, modelClient, new AiChatPromptFactory(),
                currentUserProvider, new AiChatProperties());
    }

    @Test
    void metricsReachTheModelCompactlyAndSummaryIsReturned() {
        stubUser(1L);
        when(dashboardService.getDashboard(DashboardPeriod.LAST_WEEK)).thenReturn(dashboard());
        ArgumentCaptor<List<AiChatModelClient.GatewayMessage>> captor =
                ArgumentCaptor.forClass(List.class);
        when(modelClient.generateText(anyString(), captor.capture(), anyInt()))
                .thenReturn("A good week.");

        AiDashboardSummaryResponse response = service.getSummary(DashboardPeriod.LAST_WEEK, "en");

        assertThat(response.summary()).isEqualTo("A good week.");
        assertThat(response.cached()).isFalse();
        String metrics = captor.getValue().getFirst().content();
        assertThat(metrics).contains("totalRevenueEGP: 1500.50")
                .contains("totalOrders: 12")
                .contains("PANADOL x40");
    }

    @Test
    void secondCallWithinTtlIsServedFromCache() {
        stubUser(1L);
        when(dashboardService.getDashboard(DashboardPeriod.LAST_WEEK)).thenReturn(dashboard());
        when(modelClient.generateText(anyString(), any(), anyInt())).thenReturn("Summary.");

        AiDashboardSummaryResponse first = service.getSummary(DashboardPeriod.LAST_WEEK, "en");
        AiDashboardSummaryResponse second = service.getSummary(DashboardPeriod.LAST_WEEK, "en");

        assertThat(first.cached()).isFalse();
        assertThat(second.cached()).isTrue();
        verify(modelClient, times(1)).generateText(anyString(), any(), anyInt());
    }

    @Test
    void cacheIsIsolatedPerUserAndPeriod() {
        when(dashboardService.getDashboard(any())).thenReturn(dashboard());
        when(modelClient.generateText(anyString(), any(), anyInt())).thenReturn("Summary.");

        stubUser(1L);
        service.getSummary(DashboardPeriod.LAST_WEEK, "en");
        service.getSummary(DashboardPeriod.LAST_MONTH, "en");
        stubUser(2L);
        service.getSummary(DashboardPeriod.LAST_WEEK, "en");

        verify(modelClient, times(3)).generateText(anyString(), any(), anyInt());
    }

    @Test
    void modelFailureFallsBackToNumericSummary() {
        stubUser(1L);
        when(dashboardService.getDashboard(DashboardPeriod.LAST_WEEK)).thenReturn(dashboard());
        when(modelClient.generateText(anyString(), any(), anyInt()))
                .thenThrow(new IllegalStateException("gateway down"));

        AiDashboardSummaryResponse response = service.getSummary(DashboardPeriod.LAST_WEEK, "en");

        assertThat(response.summary()).contains("EGP 1500.50").contains("12").contains("PANADOL");
    }

    @Test
    void accessDeniedFromDashboardPropagatesBeforeAnyCacheRead() {
        stubUser(9L);
        when(dashboardService.getDashboard(DashboardPeriod.LAST_WEEK))
                .thenThrow(new AccessDeniedException("Only the pharmacy admin"));

        assertThatThrownBy(() -> service.getSummary(DashboardPeriod.LAST_WEEK, "en"))
                .isInstanceOf(AccessDeniedException.class);
    }

    private void stubUser(Long id) {
        User user = new User();
        user.setId(id);
        when(currentUserProvider.get()).thenReturn(user);
    }

    private PharmacyDashboardResponse dashboard() {
        return new PharmacyDashboardResponse(
                new BigDecimal("1500.50"), 12, 30, 18,
                List.of(new MostSoldProductResponse(7L, "PANADOL", "https://x/img.png", 40,
                        new BigDecimal("400.00"))),
                List.of()
        );
    }
}
