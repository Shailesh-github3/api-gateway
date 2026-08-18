package com.gateway.usage.scheduler;

import com.gateway.usage.service.RollupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RollupScheduler {

    private final RollupService rollupService;

    // Runs at 2:00 AM every day
    @Scheduled(cron = "0 0 2 * * *")
    public void executeRollup() {
        log.info("Scheduled rollup job triggered");
        try {
            rollupService.rollupYesterday();
        } catch (Exception e) {
            log.error("Rollup job failed", e);
            // In production, you'd send this to a monitoring system
        }
    }
}