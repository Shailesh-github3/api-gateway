package com.gateway.auth;

import com.gateway.BaseIntegrationTest;
import com.gateway.apikey.entity.ApiKey;
import com.gateway.apikey.repository.ApiKeyRepository;
import com.gateway.common.util.HashUtil;
import com.gateway.common.util.KeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private KeyGenerator keyGenerator;

    @Autowired
    private HashUtil hashUtil;

    private String validKeyWithScope;
    private String validKeyWithoutScope;

    @BeforeEach
    void setUp() {
        apiKeyRepository.deleteAll();

        // Create a key WITH the required 'read' scope
        validKeyWithScope = createTestKey(new String[]{"read", "write"});

        // Create a key WITHOUT the required 'read' scope
        validKeyWithoutScope = createTestKey(new String[]{"write"});
    }

    private String createTestKey(String[] scopes) {
        String rawKey = keyGenerator.generate();
        ApiKey apiKey = new ApiKey();
        apiKey.setOwnerId(1L);
        apiKey.setKeyPrefix(rawKey.substring(0, 12));
        apiKey.setKeyHash(hashUtil.hash(rawKey));
        apiKey.setTier("STARTER");
        apiKey.setScopes(scopes);
        apiKeyRepository.save(apiKey);
        return rawKey;
    }

    @Test
    void shouldReturn401WhenNoApiKeyProvided() throws Exception {
        mockMvc.perform(get("/v1/example-resource"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401WhenInvalidApiKeyProvided() throws Exception {
        mockMvc.perform(get("/v1/example-resource")
                        .header("X-API-Key", "sk_live_invalidkey1234567890"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn403WhenKeyLacksRequiredScope() throws Exception {
        mockMvc.perform(get("/v1/example-resource")
                        .header("X-API-Key", validKeyWithoutScope))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn200WhenKeyIsValidAndHasScope() throws Exception {
        // The DemoResourceController is now built, so it should return 200 OK.
        // This PROVES the request successfully passed Auth, Scope, and Rate Limit filters.
        mockMvc.perform(get("/v1/example-resource")
                        .header("X-API-Key", validKeyWithScope))
                .andExpect(status().isOk()); // Changed from isNotFound() to isOk()
    }
}