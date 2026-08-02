package com.gateway.apikey.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ApiKeyResponse {
    private Long id;
    private Long ownerId;
    private String tier;
    private String[] scopes;
    private boolean revoked;
    private LocalDateTime createdAt;
}