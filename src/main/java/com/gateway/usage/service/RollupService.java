package com.gateway.usage.service;

import com.gateway.usage.repository.UsageRollupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RollupService {

    private final UsageRollupRepository rollupRepository;

    @Transactional
    public void rollupYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("Starting rollup for day: {}", yesterday);

        rollupRepository.rollupDay(yesterday);

        log.info("Rollup complete. Deleting events older than 30 days.");
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        rollupRepository.deleteOlderThan(cutoff);

        log.info("Cleanup complete.");
    }
}