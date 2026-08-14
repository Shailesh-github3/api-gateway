package com.gateway.apikey.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiKeyMetadata implements Serializable {

    private Long id;
    private String keyHash;
    private String tier;
    private String[] scopes;
    private boolean revoked;

}
