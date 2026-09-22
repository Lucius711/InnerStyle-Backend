package com.innerstyle.wallet.gateway;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * HMAC-SHA256 helper for gateway signatures (lower-case hex).
 */
@Component
public class CryptoSigner {

    public String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HmacSHA256", e);
        }
    }

    /**
     * Constant-time comparison to avoid timing side-channels on signature checks. Hex is compared
     * case-insensitively (finding n1): our HMAC output is lower-case, but a gateway may return
     * upper-case hex, which must still verify.
     */
    public boolean matches(String expected, String actual) {
        if (expected == null || actual == null || expected.length() != actual.length()) {
            return false;
        }
        String a = expected.toLowerCase();
        String b = actual.toLowerCase();
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
