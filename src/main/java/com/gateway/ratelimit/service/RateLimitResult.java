package com.gateway.ratelimit.service;

import io.github.bucket4j.ConsumptionProbe;

public record RateLimitResult(ConsumptionProbe probe, long limit) {
}
