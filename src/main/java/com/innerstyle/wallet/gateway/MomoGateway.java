package com.innerstyle.wallet.gateway;

import com.innerstyle.common.exception.BadRequestException;
import com.innerstyle.common.exception.UpstreamServiceException;
import com.innerstyle.wallet.config.PaymentProperties;
import com.innerstyle.wallet.entity.PaymentOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Creates MoMo one-time payments (captureWallet) and verifies MoMo IPN callbacks.
 * Signature: HMAC-SHA256 over a fixed-order {@code key=value&...} string.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MomoGateway {

    private static final String REQUEST_TYPE = "captureWallet";

    private final PaymentProperties props;
    private final CryptoSigner signer;
    private final RestClient restClient = RestClient.create();

    /** Create a MoMo payment and return the URL the client opens to pay. */
    @SuppressWarnings("unchecked")
    public String createPayment(PaymentOrder order) {
        PaymentProperties.Momo cfg = props.momo();
        if (isBlank(cfg.partnerCode()) || isBlank(cfg.accessKey()) || isBlank(cfg.secretKey())) {
            throw new BadRequestException("payment.momo.notConfigured");
        }
        String orderId = order.getOrderCode();
        String requestId = order.getOrderCode() + "-" + System.currentTimeMillis();
        String amount = order.getAmount().toBigInteger().toString();
        String orderInfo = "Topup wallet " + orderId;
        String extraData = "";

        String rawSignature = "accessKey=" + cfg.accessKey()
            + "&amount=" + amount
            + "&extraData=" + extraData
            + "&ipnUrl=" + cfg.ipnUrl()
            + "&orderId=" + orderId
            + "&orderInfo=" + orderInfo
            + "&partnerCode=" + cfg.partnerCode()
            + "&redirectUrl=" + cfg.redirectUrl()
            + "&requestId=" + requestId
            + "&requestType=" + REQUEST_TYPE;
        String signature = signer.hmacSha256Hex(cfg.secretKey(), rawSignature);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", cfg.partnerCode());
        body.put("partnerName", "InnerStyle");
        body.put("storeId", "InnerStyle");
        body.put("requestId", requestId);
        body.put("amount", Long.parseLong(amount));
        body.put("orderId", orderId);
        body.put("orderInfo", orderInfo);
        body.put("redirectUrl", cfg.redirectUrl());
        body.put("ipnUrl", cfg.ipnUrl());
        body.put("lang", "vi");
        body.put("extraData", extraData);
        body.put("requestType", REQUEST_TYPE);
        body.put("signature", signature);

        Map<String, Object> response;
        try {
            response = restClient.post()
                .uri(cfg.endpoint())
                .body(body)
                .retrieve()
                .body(Map.class);
        } catch (Exception ex) {
            log.error("MoMo create payment failed: {}", ex.getMessage());
            throw new UpstreamServiceException("payment.momo.unavailable");
        }
        if (response == null || response.get("payUrl") == null) {
            log.error("MoMo create returned no payUrl: {}", response);
            throw new UpstreamServiceException("payment.momo.createFailed");
        }
        return String.valueOf(response.get("payUrl"));
    }

    /** Verify a MoMo IPN callback and translate it to a {@link GatewayVerification}. */
    public GatewayVerification verify(Map<String, String> p) {
        PaymentProperties.Momo cfg = props.momo();
        String rawSignature = "accessKey=" + cfg.accessKey()
            + "&amount=" + p.get("amount")
            + "&extraData=" + nz(p.get("extraData"))
            + "&message=" + nz(p.get("message"))
            + "&orderId=" + p.get("orderId")
            + "&orderInfo=" + nz(p.get("orderInfo"))
            + "&orderType=" + nz(p.get("orderType"))
            + "&partnerCode=" + p.get("partnerCode")
            + "&payType=" + nz(p.get("payType"))
            + "&requestId=" + p.get("requestId")
            + "&responseTime=" + nz(p.get("responseTime"))
            + "&resultCode=" + p.get("resultCode")
            + "&transId=" + p.get("transId");
        String expected = signer.hmacSha256Hex(cfg.secretKey(), rawSignature);
        boolean signatureValid = signer.matches(expected, nz(p.get("signature")));

        String resultCode = p.get("resultCode");
        boolean success = "0".equals(resultCode);
        BigDecimal amount = p.get("amount") == null ? null : new BigDecimal(p.get("amount"));
        return new GatewayVerification(signatureValid, success, p.get("orderId"),
            amount, p.get("transId"), resultCode);
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
