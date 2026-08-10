package com.gateway.ratelimit.repository;

import com.gateway.ratelimit.entity.RateLimitTier;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RateLimitTierRepository extends JpaRepository<RateLimitTier,String> {
}
