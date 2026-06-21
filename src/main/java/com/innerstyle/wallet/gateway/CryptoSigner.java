package com.innerstyle.wallet.gateway;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * HMAC helpers for gateway signatures: VNPay uses HMAC-SHA512, MoMo uses HMAC-SHA256
 * (both lower-case hex).
 */
@Component
public class CryptoSigner {

    public String hmacSha512Hex(String secret, String data) {
        return hmacHex("HmacSHA512", secret, data);
    }

    public String hmacSha256Hex(String secret, String data) {
        return hmacHex("HmacSHA256", secret, data);
    }

    private String hmacHex(String algorithm, String secret, String data) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
            byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute " + algorithm, e);
        }
    }

    /** Constant-time comparison to avoid timing side-channels on signature checks. */
    public boolean matches(String expected, String actual) {
        if (expected == null || actual == null || expected.length() != actual.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < expected.length(); i++) {
            diff |= expected.charAt(i) ^ actual.charAt(i);
        }
        return diff == 0;
    }
}
