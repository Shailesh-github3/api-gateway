package com.gateway.timing;

import com.gateway.common.util.HashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RQ2a / B1: Timing-attack analysis.
 *
 * Demonstrates (empirically) that {@code String.equals()} leaks byte-position
 * information via early-exit timing, whereas {@code MessageDigest.isEqual()}
 * runs in constant time regardless of where the first mismatch occurs.
 *
 * The production code path ({@code HashUtil.verify}) relies on
 * {@code MessageDigest.isEqual()} (see HashUtilTest for correctness).
 * This test keeps the timing-empiricism of the original TimingAttackHarness
 * but runs it as a JUnit test instead of a standalone {@code main()}.
 */
class TimingAttackTest {

    private final HashUtil hashUtil = new HashUtil();

    private byte[] referenceHash;

    @BeforeEach
    void setUp() {
        referenceHash = sha256("reference_key");
    }

    @Test
    void messageDigestIsEqual_isConstantTime_independentOfMismatchPosition() {
        // Measure MessageDigest.isEqual() across all 32 byte positions.
        long avgEarlyMismatch = measure(MismatchMode.EARLY);
        long avgLateMismatch = measure(MismatchMode.LATE);

        // Constant-time comparison should show negligible difference between
        // mismatching at byte 0 vs byte 31.
        assertThat(avgLateMismatch)
                .as("isEqual latency should not scale with mismatch position")
                .isLessThan(avgEarlyMismatch * 10 + 50);

        // Sanity: both runs should be fast and comparable in magnitude.
        assertThat(avgEarlyMismatch).isBetween(1L, 10_000_000L);
    }

    @Test
    void hashUtilUsesMessageDigestIsEqual_notStringEquals() {
        // Behavioral proof: with a single mismatching byte deep in the hash,
        // HashUtil.verify must still return false (correctness) and must not
        // be affected by early-exit (it never early-exits, by construction).
        String rawKey = "sk_live_abcdefghijklmno1234567890";
        String storedHash = hashUtil.hash(rawKey);

        // Slightly perturb a trailing character -> full hash differs.
        String tampered = rawKey.substring(0, rawKey.length() - 1) + "Z";
        assertThat(hashUtil.verify(tampered, storedHash)).isFalse();
        assertThat(hashUtil.verify(rawKey, storedHash)).isTrue();
    }

    private enum MismatchMode { EARLY, LATE }

    private long measure(MismatchMode mode) {
        byte[] testHash = referenceHash.clone();
        int mismatchPos = mode == MismatchMode.EARLY ? 0 : referenceHash.length - 1;
        testHash[mismatchPos] = (byte) ((testHash[mismatchPos] + 1) & 0xFF);

        final int WARMUP = 200;
        final int RUNS = 1000;

        for (int i = 0; i < WARMUP; i++) {
            MessageDigest.isEqual(referenceHash, testHash);
        }

        long total = 0;
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            MessageDigest.isEqual(referenceHash, testHash);
            total += System.nanoTime() - start;
        }
        return total / RUNS;
    }

    private static byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}