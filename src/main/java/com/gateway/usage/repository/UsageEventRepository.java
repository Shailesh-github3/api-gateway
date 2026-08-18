package com.gateway.usage.repository;

import com.gateway.usage.entity.UsageEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsageEventRepository extends JpaRepository<UsageEvent, Long> {
}
