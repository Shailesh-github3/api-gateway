package com.gateway.ratelimit.service;

import com.gateway.apikey.entity.ApiKey;
import com.gateway.ratelimit.entity.RateLimitTier;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.redis.jedis.cas.JedisBasedProxyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final JedisBasedProxyManager<String> proxyManager;
    private final TierService tierService;

    public ConsumptionProbe tryConsume(ApiKey apiKey) {
        RateLimitTier tier = tierService.getTier(apiKey.getTier());

        // Version-correct Bucket4j 8.19.0 configuration
        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(limit -> limit
                        .capacity(tier.getBurstCapacity())
                        .refillGreedy(tier.getRequestsPerMinute(), Duration.ofMinutes(1)))
                .build();

        String bucketKey = "bucket:" + apiKey.getId();
        Bucket bucket = proxyManager.getProxy(bucketKey, () -> config);

        return bucket.tryConsumeAndReturnRemaining(1);
    }
}