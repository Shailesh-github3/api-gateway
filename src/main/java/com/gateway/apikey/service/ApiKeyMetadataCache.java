package com.gateway.apikey.service;


import tools.jackson.core.JacksonException;
import com.gateway.apikey.dto.ApiKeyMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyMetadataCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String KEY_PREFIX = "apikey:meta:";
    private static final Duration TTL = Duration.ofSeconds(60);

    public Optional<ApiKeyMetadata> get(String prefix) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + prefix);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, ApiKeyMetadata.class));
        } catch (JacksonException e) {
            log.error("Failed to deserialize cached metadata for prefix: {}", prefix, e);
            redisTemplate.delete(KEY_PREFIX + prefix); // Evict corrupted entry
            return Optional.empty();
        }
    }

    public void put(String prefix, ApiKeyMetadata metadata) {
        try {
            String json = objectMapper.writeValueAsString(metadata);
            redisTemplate.opsForValue().set(KEY_PREFIX + prefix, json, TTL);
        } catch (JacksonException e) {
            log.error("Failed to serialize metadata for prefix: {}", prefix, e);
            // Fail silently - cache is an optimization, not a requirement
        }
    }

    public void evict(String prefix) {
        redisTemplate.delete(KEY_PREFIX + prefix);
    }

}
