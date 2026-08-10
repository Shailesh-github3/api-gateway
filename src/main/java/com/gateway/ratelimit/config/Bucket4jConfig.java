package com.gateway.ratelimit.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.serialization.Mapper;
import io.github.bucket4j.redis.jedis.Bucket4jJedis;
import io.github.bucket4j.redis.jedis.cas.JedisBasedProxyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class Bucket4jConfig {

    private final JedisPool jedisPool;

    @Bean
    public JedisBasedProxyManager<String> bucketProxyManager() {
        return Bucket4jJedis.casBasedBuilder(jedisPool)
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10)))
                .keyMapper(Mapper.STRING)
                .build();
    }
}