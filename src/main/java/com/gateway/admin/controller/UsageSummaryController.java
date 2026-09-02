package com.gateway.admin.controller;

import com.gateway.usage.entity.UsageDailyRollup;
import com.gateway.usage.repository.UsageRollupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/usage")
@RequiredArgsConstructor
public class UsageSummaryController {

    private final UsageRollupRepository usageRollupRepository;

    @GetMapping("/{apiKeyId}")
    public ResponseEntity<List<UsageDailyRollup>> getUsageSummary(@PathVariable Long apiKeyId) {
        return ResponseEntity.ok(usageRollupRepository.findByApiKeyIdOrderByDayDesc(apiKeyId));
    }
}
