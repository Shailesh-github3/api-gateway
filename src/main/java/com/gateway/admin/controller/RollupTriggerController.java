package com.gateway.admin.controller;

import com.gateway.usage.service.RollupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/rollup")
@RequiredArgsConstructor
public class RollupTriggerController {

    private final RollupService rollupService;

    @PostMapping
    public ResponseEntity<String> triggerRollup() {
        rollupService.rollupYesterday();
        return ResponseEntity.ok("Rollup executed");
    }
}