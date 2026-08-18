package com.gateway.usage.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity
@Table(name = "usage_daily_rollup")
@Getter
@Setter
public class UsageDailyRollup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_key_id", nullable = false)
    private Long apiKeyId;

    @Column(name = "day", nullable = false)
    private LocalDate day;

    @Column(name = "request_count", nullable = false)
    private long requestCount;

    @Column(name = "error_count", nullable = false)
    private long errorCount;

    @Column(name = "avg_latency_ms", nullable = false)
    private long avgLatencyMs;
}