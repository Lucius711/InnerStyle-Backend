package com.innerstyle.auth.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link TokenHasher} — OTP generation bounds, numeric/zero-padded format,
 * SHA-256 hashing determinism, and opaque-token uniqueness.
 */
class TokenHasherTest {

    private final TokenHasher hasher = new TokenHasher();

    @ParameterizedTest
    @ValueSource(ints = {4, 5, 6, 7, 8, 9})
    @DisplayName("generateOtp: returns numeric code zero-padded to the requested length")
    void generateOtp_validLength_returnsPaddedNumeric(int length) {
        for (int i = 0; i < 200; i++) {
            String otp = hasher.generateOtp(length);
            assertThat(otp).hasSize(length);
            assertThat(otp).matches("\\d{" + length + "}");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 10, 12, -1})
    @DisplayName("generateOtp: length outside 4..9 is rejected")
    void generateOtp_invalidLength_throws(int length) {
        assertThatThrownBy(() -> hasher.generateOtp(length))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("generateOtp: produces varied values (not constant)")
    void generateOtp_isRandom() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            seen.add(hasher.generateOtp(6));
        }
        // 500 draws from 1e6 space should yield many distinct values.
        assertThat(seen.size()).isGreaterThan(400);
    }

    @Test
    @DisplayName("hash: deterministic 64-char hex digest")
    void hash_isDeterministicHex() {
        String h1 = hasher.hash("123456");
        String h2 = hasher.hash("123456");
        assertThat(h1).isEqualTo(h2);
        assertThat(h1).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("hash: different inputs → different digests")
    void hash_differsByInput() {
        assertThat(hasher.hash("123456")).isNotEqualTo(hasher.hash("123457"));
    }

    @Test
    @DisplayName("generateToken: unique, URL-safe, no padding")
    void generateToken_unique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String t = hasher.generateToken();
            assertThat(t).matches("[A-Za-z0-9_-]+");
            seen.add(t);
        }
        assertThat(seen).hasSize(500);
    }
}
