package com.gateway.usage.service;

import com.gateway.usage.entity.UsageEvent;
import com.gateway.usage.repository.UsageEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsageLoggerService {

    private final UsageEventRepository usageEventRepository;

    // CRITICAL: Must be public, and must specify the executor bean name
    @Async("usageLogExecutor")
    public void logUsage(UsageEvent event) {
        try {
            usageEventRepository.save(event);
        } catch (Exception e) {
            // In production, you would send this to a dead-letter queue or metrics system.
            // For this project, logging the failure is sufficient to prove you know
            // that @Async void methods swallow exceptions by default.
            log.error("Failed to save usage event for apiKeyId: {}", event.getApiKeyId(), e);
        }
    }
}