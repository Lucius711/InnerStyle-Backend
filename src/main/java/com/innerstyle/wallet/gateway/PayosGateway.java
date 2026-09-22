package com.innerstyle.wallet.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innerstyle.common.exception.BadRequestException;
import com.innerstyle.common.exception.UpstreamServiceException;
import com.innerstyle.wallet.config.PaymentProperties;
import com.innerstyle.wallet.entity.PaymentOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Creates payOS payment links and verifies payOS callbacks (webhook + return-page lookup).
 * Signature: HMAC-SHA256 over the alphabetically-sorted {@code key=value&...} string of the
 * signed field set — payOS's official algorithm (see https://payos.vn/docs).
 *
 * <p>payOS's browser {@code returnUrl} carries no signature, so a return is confirmed by looking
 * the order up directly at payOS ({@link #verifyReturn}) rather than trusting the redirect query
 * params.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayosGateway {

    private final PaymentProperties props;
    private final CryptoSigner signer;
    private final ObjectMapper objectMapper;
    // Explicit connect/read timeouts (finding M4): a slow/unreachable payOS endpoint must not
    // block the servlet thread indefinitely.
    private final RestClient restClient = RestClient.builder()
        .requestFactory(payosRequestFactory())
        .build();

    private static SimpleClientHttpRequestFactory payosRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        return factory;
    }

    /** Checkout URL (hosted payOS page) plus the raw VietQR payload for an in-app QR. */
    public record Checkout(String checkoutUrl, String qrCode) {
    }

    /** Create a payOS payment link. */
    @SuppressWarnings("unchecked")
    public Checkout createPaymentLink(PaymentOrder order) {
        PaymentProperties.Payos cfg = props.payos();
        if (isBlank(cfg.clientId()) || isBlank(cfg.apiKey()) || isBlank(cfg.checksumKey())) {
            throw new BadRequestException("payment.payos.notConfigured");
        }
        long orderCode = Long.parseLong(order.getOrderCode());
        long amount = order.getAmount().toBigInteger().longValueExact();
        // payOS caps description length; keep it short and predictable.
        String description = truncate("Thanh toan " + order.getOrderCode(), 25);

        // Signed field set for link creation is fixed by payOS: amount, cancelUrl, description,
        // orderCode, returnUrl (sorted alphabetically).
        Map<String, Object> signedFields = new TreeMap<>();
        signedFields.put("amount", amount);
        signedFields.put("cancelUrl", cfg.cancelUrl());
        signedFields.put("description", description);
        signedFields.put("orderCode", orderCode);
        signedFields.put("returnUrl", cfg.returnUrl());
        String signature = signer.hmacSha256Hex(cfg.checksumKey(), buildSortedPayload(signedFields));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderCode", orderCode);
        body.put("amount", amount);
        body.put("description", description);
        body.put("cancelUrl", cfg.cancelUrl());
        body.put("returnUrl", cfg.returnUrl());
        body.put("signature", signature);

        Map<String, Object> response;
        try {
            response = restClient.post()
                .uri(cfg.endpoint() + "/v2/payment-requests")
                .header("x-client-id", cfg.clientId())
                .header("x-api-key", cfg.apiKey())
                .body(body)
                .retrieve()
                .body(Map.class);
        } catch (Exception ex) {
            log.error("payOS create payment link failed: {}", ex.getMessage());
            throw new UpstreamServiceException("payment.payos.unavailable");
        }
        Object data = response == null ? null : response.get("data");
        Map<?, ?> d = data instanceof Map<?, ?> m ? m : null;
        Object checkoutUrl = d == null ? null : d.get("checkoutUrl");
        if (checkoutUrl == null) {
            log.error("payOS create returned no checkoutUrl: {}", response);
            throw new UpstreamServiceException("payment.payos.createFailed");
        }
        Object qrCode = d.get("qrCode");
        return new Checkout(String.valueOf(checkoutUrl), qrCode == null ? null : String.valueOf(qrCode));
    }

    /** Verify a payOS webhook payload ({@code {code, desc, success, data, signature}}). */
    @SuppressWarnings("unchecked")
    public GatewayVerification verifyWebhook(Map<String, Object> payload) {
        Object dataObj = payload.get("data");
        Map<String, Object> data = dataObj instanceof Map<?, ?> d ? (Map<String, Object>) d : Map.of();
        String providedSignature = payload.get("signature") == null ? null : String.valueOf(payload.get("signature"));
        boolean success = Boolean.TRUE.equals(payload.get("success"));
        return verifyData(data, providedSignature, success);
    }

    /**
     * Confirm a browser return by looking the order's authoritative status up directly at payOS
     * (the {@code returnUrl} query params carry no signature and must not be trusted).
     */
    @SuppressWarnings("unchecked")
    public GatewayVerification verifyReturn(Map<String, String> params) {
        PaymentProperties.Payos cfg = props.payos();
        String orderCode = params.get("orderCode");
        if (isBlank(orderCode)) {
            return new GatewayVerification(false, false, null, null, null, null);
        }
        Map<String, Object> response;
        try {
            response = restClient.get()
                .uri(cfg.endpoint() + "/v2/payment-requests/{id}", orderCode)
                .header("x-client-id", cfg.clientId())
                .header("x-api-key", cfg.apiKey())
                .retrieve()
                .body(Map.class);
        } catch (Exception ex) {
            log.error("payOS get payment link info failed: {}", ex.getMessage());
            return new GatewayVerification(false, false, orderCode, null, null, null);
        }
        Object dataObj = response == null ? null : response.get("data");
        Map<String, Object> data = dataObj instanceof Map<?, ?> d ? (Map<String, Object>) d : Map.of();
        String providedSignature = response == null || response.get("signature") == null
            ? null : String.valueOf(response.get("signature"));
        boolean success = "PAID".equals(data.get("status"));
        return verifyData(data, providedSignature, success);
    }

    private GatewayVerification verifyData(Map<String, Object> data, String providedSignature, boolean success) {
        String expected = signer.hmacSha256Hex(props.payos().checksumKey(), buildSortedPayload(data));
        boolean signatureValid = signer.matches(expected, providedSignature == null ? "" : providedSignature);

        Object orderCodeVal = data.get("orderCode");
        Object amountVal = data.get("amount");
        // The webhook carries the bank/e-wallet transaction reference; the return-page lookup
        // only has the payOS payment-link id, which is still unique per order.
        Object refVal = data.get("reference") != null ? data.get("reference") : data.get("id");
        Object codeVal = data.get("code");

        return new GatewayVerification(
            signatureValid,
            success,
            orderCodeVal == null ? null : String.valueOf(orderCodeVal),
            amountVal == null ? null : new BigDecimal(String.valueOf(amountVal)),
            refVal == null ? null : String.valueOf(refVal),
            codeVal == null ? null : String.valueOf(codeVal)
        );
    }

    /**
     * payOS signs the alphabetically-sorted {@code key=value&...} string of a field set. A null
     * value signs as an empty string; a nested object/array signs as its (key-sorted) JSON.
     */
    private String buildSortedPayload(Map<String, Object> fields) {
        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, Object> e : new TreeMap<>(fields).entrySet()) {
            pairs.add(e.getKey() + "=" + stringifyValue(e.getValue()));
        }
        return String.join("&", pairs);
    }

    private String stringifyValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            try {
                return objectMapper.writeValueAsString(sortDeep(value));
            } catch (Exception e) {
                return String.valueOf(value);
            }
        }
        return String.valueOf(value);
    }

    /** Recursively sorts map keys so nested-object signing is deterministic. */
    private Object sortDeep(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                sorted.put(String.valueOf(e.getKey()), sortDeep(e.getValue()));
            }
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> sorted = new ArrayList<>();
            for (Object item : list) {
                sorted.add(sortDeep(item));
            }
            return sorted;
        }
        return value;
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
