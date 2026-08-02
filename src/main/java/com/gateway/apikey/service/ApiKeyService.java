package com.gateway.apikey.service;

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
        Optional<ApiKey> optionalApiKey = apiKeyRepository.findByKeyPrefix(prefix);

        if (optionalApiKey.isEmpty()) {
            return Optional.empty();
        }

        ApiKey apiKey = optionalApiKey.get();

        if (apiKey.isRevoked()) {
            return Optional.empty();
        }

        boolean isValid = hashUtil.verify(rawKey, apiKey.getKeyHash());
        return isValid ? Optional.of(apiKey) : Optional.empty();
    }


    @Transactional
    public void revoke(Long id) {
        ApiKey apiKey = apiKeyRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("API key not found"));
        apiKey.setRevoked(true);
        apiKeyRepository.save(apiKey);

        // TODO: Week 2 - Explicitly delete Redis cache entry here
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