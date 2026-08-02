package com.gateway.common.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class KeyGenerator {
    private static final String PREFIX = "sk_live_";
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        // 24 bytes = 32 Base64 characters (24 * 8 = 192 bits of entropy)
        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);

        // URL-safe Base64 avoids '+' and '/' which require URL encoding
        String base64String = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return PREFIX + base64String;
    }
}
