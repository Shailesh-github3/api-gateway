package com.gateway.ratelimit.service;

import com.gateway.apikey.entity.ApiKey;
import com.gateway.ratelimit.entity.RateLimitTier;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.redis.jedis.cas.JedisBasedProxyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private JedisBasedProxyManager<String> proxyManager;

    @Mock
    private TierService tierService;

    @Mock
    private BucketProxy bucket;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService(proxyManager, tierService);
    }

    @Test
    void tryConsume_usesBucketKeyedByApiKeyId() {
        ApiKey apiKey = apiKey(7L, "STARTER");
        RateLimitTier tier = new RateLimitTier();
        tier.setTier("STARTER");
        tier.setRequestsPerMinute(60);
        tier.setBurstCapacity(10);

        ConsumptionProbe consumed = ConsumptionProbe.consumed(9L, 60_000_000_000L);

        when(tierService.getTier("STARTER")).thenReturn(tier);
        when(proxyManager.getProxy(eq("bucket:7"), any())).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(consumed);

        ConsumptionProbe result = rateLimitService.tryConsume(apiKey);

        assertThat(result.isConsumed()).isTrue();
        assertThat(result.getRemainingTokens()).isEqualTo(9L);
        verify(proxyManager).getProxy(eq("bucket:7"), any());
        verify(bucket).tryConsumeAndReturnRemaining(1);
    }

    @Test
    void tryConsume_rejectedProbePropagated() {
        ApiKey apiKey = apiKey(3L, "PRO");
        RateLimitTier tier = new RateLimitTier();
        tier.setTier("PRO");
        tier.setRequestsPerMinute(600);
        tier.setBurstCapacity(100);

        ConsumptionProbe rejected = ConsumptionProbe.rejected(0L, 1000L, 60_000_000_000L);

        when(tierService.getTier("PRO")).thenReturn(tier);
        when(proxyManager.getProxy(anyString(), any())).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(rejected);

        ConsumptionProbe result = rateLimitService.tryConsume(apiKey);

        assertThat(result.isConsumed()).isFalse();
        assertThat(result.getNanosToWaitForRefill()).isEqualTo(1000L);
    }

    private ApiKey apiKey(Long id, String tier) {
        ApiKey apiKey = new ApiKey();
        apiKey.setId(id);
        apiKey.setTier(tier);
        return apiKey;
    }
}