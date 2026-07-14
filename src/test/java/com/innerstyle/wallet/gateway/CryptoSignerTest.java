package com.innerstyle.wallet.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CryptoSigner}: deterministic HMAC output and the case-insensitive,
 * constant-time signature comparison (finding n1).
 */
class CryptoSignerTest {

    private final CryptoSigner signer = new CryptoSigner();

    @Test
    @DisplayName("HMAC-SHA256/512 are deterministic and lower-case hex")
    void hmacIsDeterministic() {
        String a256 = signer.hmacSha256Hex("secret", "data");
        String b256 = signer.hmacSha256Hex("secret", "data");
        assertThat(a256).isEqualTo(b256).isEqualTo(a256.toLowerCase());

        String a512 = signer.hmacSha512Hex("secret", "data");
        assertThat(signer.hmacSha512Hex("secret", "data")).isEqualTo(a512);
        assertThat(a512).isNotEqualTo(a256);
    }

    @Test
    @DisplayName("a different secret yields a different signature")
    void differentSecretDiffersSignature() {
        assertThat(signer.hmacSha256Hex("secret-a", "data"))
            .isNotEqualTo(signer.hmacSha256Hex("secret-b", "data"));
    }

    @Test
    @DisplayName("matches is case-insensitive (n1): upper-case gateway hex still verifies")
    void matchesIsCaseInsensitive() {
        String expected = signer.hmacSha256Hex("secret", "data");
        assertThat(signer.matches(expected, expected.toUpperCase())).isTrue();
        assertThat(signer.matches(expected, expected)).isTrue();
    }

    @Test
    @DisplayName("matches rejects a different value and a length mismatch, and null-safely")
    void matchesRejectsMismatch() {
        String expected = signer.hmacSha256Hex("secret", "data");
        // Flip the first char to a guaranteed-different hex digit (same length).
        char first = expected.charAt(0);
        String wrong = (first == 'a' ? 'b' : 'a') + expected.substring(1);
        assertThat(signer.matches(expected, wrong)).isFalse();
        assertThat(signer.matches(expected, expected + "ab")).isFalse();
        assertThat(signer.matches(expected, null)).isFalse();
        assertThat(signer.matches(null, expected)).isFalse();
    }
}
