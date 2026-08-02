package com.gateway.apikey.repository;

import com.gateway.apikey.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    Optional<ApiKey> findByKeyPrefix(String keyPrefix);
}
