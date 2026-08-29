package com.gateway.usage.scheduler;

import com.gateway.usage.service.RollupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RollupScheduler {

    private final RollupService rollupService;

    // Runs at 2:00 AM every day
    @Scheduled(cron = "0 0 2 * * *")
    @SchedulerLock(
            name = "nightlyRollupJob",
            lockAtMostFor = "15m",  // Auto-release if job hangs for >15m
            lockAtLeastFor = "30s"  // Prevent rapid re-runs
    )
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