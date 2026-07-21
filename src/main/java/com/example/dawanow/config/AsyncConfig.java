package com.example.dawanow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables @Async (used by NotificationDispatcher.dispatch, so FCM calls
 * never block the caller — e.g. order placement shouldn't wait on a push)
 * and @Scheduled (used by the presence sweep job and notification retry sweep).
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}