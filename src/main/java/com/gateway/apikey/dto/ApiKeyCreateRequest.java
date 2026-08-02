package com.gateway.apikey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApiKeyCreateRequest {
    @NotNull
    private Long ownerId;

    @NotBlank
    private String tier;

    @NotNull
    @Size(min = 1, message = "At least one scope is required")
    private String[] scopes;
}