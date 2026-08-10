package com.gateway.ratelimit.service;

import com.gateway.ratelimit.entity.RateLimitTier;
import com.gateway.ratelimit.repository.RateLimitTierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TierService {

    private final RateLimitTierRepository tierRepository;

    @Transactional(readOnly = true)
    public RateLimitTier getTier(String tierName) {
        return tierRepository.findById(tierName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tier: " + tierName));
    }
}