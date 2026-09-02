package com.gateway.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashUtilTest {

    private final HashUtil hashUtil = new HashUtil();

    @Test
    void hash_producesConsistentSha256() {
        String key = "sk_live_testKey123456789";
        String first = hashUtil.hash(key);
        String second = hashUtil.hash(key);
        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64);
    }

    @Test
    void hash_differentKeysProduceDifferentHashes() {
        String hash1 = hashUtil.hash("sk_live_keyOne12345678");
        String hash2 = hashUtil.hash("sk_live_keyTwo12345678");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void verify_matchingKeyReturnsTrue() {
        String rawKey = "sk_live_abcdef1234567890abcdef";
        String storedHash = hashUtil.hash(rawKey);
        assertThat(hashUtil.verify(rawKey, storedHash)).isTrue();
    }

    @Test
    void verify_mismatchedKeyReturnsFalse() {
        String rawKey = "sk_live_abcdef1234567890abcdef";
        String wrongKey = "sk_live_wrongKey1234567890ab";
        String storedHash = hashUtil.hash(rawKey);
        assertThat(hashUtil.verify(wrongKey, storedHash)).isFalse();
    }

    @Test
    void verify_emptyStringReturnsFalse() {
        String storedHash = hashUtil.hash("sk_live_realkey1234567890");
        assertThat(hashUtil.verify("", storedHash)).isFalse();
    }

    @Test
    void hash_hexCharactersAreLowercase() {
        String hash = hashUtil.hash("sk_live_anyKey1234567890");
        assertThat(hash).matches("[0-9a-f]{64}");
    }
}
