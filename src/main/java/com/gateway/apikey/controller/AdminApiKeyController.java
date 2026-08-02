package com.gateway.apikey.controller;

import com.gateway.apikey.dto.ApiKeyCreateRequest;
import com.gateway.apikey.dto.ApiKeyResponse;
import com.gateway.apikey.service.ApiKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/api-keys")
@RequiredArgsConstructor
public class AdminApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping
    public ResponseEntity<Map<String, String>> createKey(@RequestBody @Valid ApiKeyCreateRequest request) {
        String rawKey = apiKeyService.generateAndStore(
                request.getOwnerId(),
                request.getTier(),
                request.getScopes()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("apiKey", rawKey));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokeKey(@PathVariable Long id) {
        apiKeyService.revoke(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> listKeys() {
        return ResponseEntity.ok(apiKeyService.listKeys());
    }
}