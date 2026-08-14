package com.gateway.apikey.service;

import com.gateway.apikey.dto.ApiKeyMetadata;
import com.gateway.apikey.dto.ApiKeyResponse;
import com.gateway.apikey.entity.ApiKey;
import com.gateway.apikey.repository.ApiKeyRepository;
import com.gateway.common.util.HashUtil;
import com.gateway.common.util.KeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final KeyGenerator keyGenerator;
    private final ApiKeyMetadataCache metadataCache;
    private final HashUtil hashUtil;

    /**
     * Generates a new API key, stores the prefix and hash, and returns the raw key.
     * The raw key is NEVER stored and NEVER returned again after this method completes.
     */
    @Transactional
    public String generateAndStore(Long ownerId, String tier, String[] scopes) {
        String rawKey = keyGenerator.generate();
        String prefix = rawKey.substring(0, 12); // "sk_live_" (8) + 4 chars
        String hash = hashUtil.hash(rawKey);

        ApiKey apiKey = new ApiKey();
        apiKey.setOwnerId(ownerId);
        apiKey.setKeyPrefix(prefix);
        apiKey.setKeyHash(hash);
        apiKey.setTier(tier);
        apiKey.setScopes(scopes);

        apiKeyRepository.save(apiKey);
        return rawKey;
    }

    /**
     * Verifies an incoming raw key against the database.
     * Returns the ApiKey entity if valid, empty if invalid.
     */
    @Transactional(readOnly = true)
    public Optional<ApiKey> verify(String rawKey) {
        if (rawKey == null || rawKey.length() < 12) {
            return Optional.empty();
        }

        String prefix = rawKey.substring(0, 12);

        // 1. Check cache first
        Optional<ApiKeyMetadata> cachedMetadata = metadataCache.get(prefix);

        if (cachedMetadata.isPresent()) {
            ApiKeyMetadata metadata = cachedMetadata.get();

            if (metadata.isRevoked()) {
                return Optional.empty();
            }

            // Verify hash against cached value (no DB query)
            boolean isValid = hashUtil.verify(rawKey, metadata.getKeyHash());
            if (!isValid) {
                return Optional.empty();
            }

            // Reconstruct ApiKey entity from cached metadata
            ApiKey apiKey = new ApiKey();
            apiKey.setId(metadata.getId());
            apiKey.setKeyPrefix(prefix);
            apiKey.setKeyHash(metadata.getKeyHash());
            apiKey.setTier(metadata.getTier());
            apiKey.setScopes(metadata.getScopes());
            apiKey.setRevoked(metadata.isRevoked());
            return Optional.of(apiKey);
        }

        // 2. Cache miss - query DB

        Optional<ApiKey> optionalApiKey = apiKeyRepository.findByKeyPrefix(prefix);

        if (optionalApiKey.isEmpty()) {
            return Optional.empty();
        }

        ApiKey apiKey = optionalApiKey.get();

        if (apiKey.isRevoked()) {
            return Optional.empty();
        }

        boolean isValid = hashUtil.verify(rawKey, apiKey.getKeyHash());
        if (!isValid) {
            return Optional.empty();
        }

        // 3. Populate cache for next request
        ApiKeyMetadata metadata = new ApiKeyMetadata(
                apiKey.getId(),
                apiKey.getKeyHash(),
                apiKey.getTier(),
                apiKey.getScopes(),
                apiKey.isRevoked()
        );
        metadataCache.put(prefix, metadata);

        return Optional.of(apiKey);
    }


    @Transactional
    public void revoke(Long id) {
        ApiKey apiKey = apiKeyRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("API key not found"));
        apiKey.setRevoked(true);
        apiKeyRepository.save(apiKey);

        // CRITICAL: Evict cache immediately. Without this, revoked key works for up to 60s
        metadataCache.evict(apiKey.getKeyPrefix());
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listKeys() {
        return apiKeyRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    private ApiKeyResponse mapToResponse(ApiKey apiKey) {
        ApiKeyResponse response = new ApiKeyResponse();
        response.setId(apiKey.getId());
        response.setOwnerId(apiKey.getOwnerId());
        response.setTier(apiKey.getTier());
        response.setScopes(apiKey.getScopes());
        response.setRevoked(apiKey.isRevoked());
        response.setCreatedAt(apiKey.getCreatedAt());
        return response;
    }
}