package com.gateway.ratelimit.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "rate_limit_tiers")
@Getter
@Setter
public class RateLimitTier {

    @Id
    @Column(name = "tier", length = 30)
    private String tier;

    @Column(name = "requests_per_minute", nullable = false)
    private int requestsPerMinute;

    @Column(name = "burst_capacity", nullable = false)
    private int burstCapacity;
}