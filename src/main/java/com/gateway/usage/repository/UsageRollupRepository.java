package com.gateway.usage.repository;

import com.gateway.usage.entity.UsageDailyRollup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

public interface UsageRollupRepository extends JpaRepository<UsageDailyRollup, Long> {

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO usage_daily_rollup (api_key_id, day, request_count, error_count, avg_latency_ms)
        SELECT 
            api_key_id,
            DATE(created_at) as day,
            COUNT(*) as request_count,
            COUNT(CASE WHEN status_code >= 400 THEN 1 END) as error_count,
            AVG(response_time_ms)::BIGINT as avg_latency_ms
        FROM usage_events
        WHERE DATE(created_at) = :targetDay
        GROUP BY api_key_id, DATE(created_at)
        ON CONFLICT (api_key_id, day) 
        DO UPDATE SET 
            request_count = EXCLUDED.request_count,
            error_count = EXCLUDED.error_count,
            avg_latency_ms = EXCLUDED.avg_latency_ms
        """, nativeQuery = true)
    void rollupDay(@Param("targetDay") LocalDate targetDay);

    @Modifying
    @Transactional
    @Query("DELETE FROM UsageEvent WHERE createdAt < :cutoff")
    void deleteOlderThan(@Param("cutoff") java.time.LocalDateTime cutoff);
}