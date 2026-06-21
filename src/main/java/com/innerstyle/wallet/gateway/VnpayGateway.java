package com.innerstyle.wallet.gateway;

import com.innerstyle.common.exception.BadRequestException;
import com.innerstyle.wallet.config.PaymentProperties;
import com.innerstyle.wallet.entity.PaymentOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds VNPay payment URLs and verifies VNPay IPN/return callbacks.
 * Signature: HMAC-SHA512 over the URL-encoded, alphabetically-sorted parameter string,
 * following VNPay's official integration sample.
 */
@Component
@RequiredArgsConstructor
public class VnpayGateway {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PaymentProperties props;
    private final CryptoSigner signer;

    /** Build the redirect URL the client opens to pay this order. */
    public String buildPaymentUrl(PaymentOrder order, String clientIp) {
        PaymentProperties.Vnpay cfg = props.vnpay();
        if (isBlank(cfg.tmnCode()) || isBlank(cfg.hashSecret())) {
            throw new BadRequestException("payment.vnpay.notConfigured");
        }
        ZonedDateTime now = ZonedDateTime.now(VN_ZONE);

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", cfg.version());
        params.put("vnp_Command", cfg.command());
        params.put("vnp_TmnCode", cfg.tmnCode());
        // VNPay expects the amount in the smallest unit: VND * 100.
        params.put("vnp_Amount", order.getAmount().multiply(BigDecimal.valueOf(100))
            .toBigInteger().toString());
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", order.getOrderCode());
        params.put("vnp_OrderInfo", "Topup wallet " + order.getOrderCode());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", cfg.locale());
        params.put("vnp_ReturnUrl", cfg.returnUrl());
        params.put("vnp_IpAddr", clientIp == null ? "127.0.0.1" : clientIp);
        params.put("vnp_CreateDate", now.format(FMT));
        params.put("vnp_ExpireDate", now.plus(props.orderTtl()).format(FMT));

        String hashData = buildEncoded(params);
        String secureHash = signer.hmacSha512Hex(cfg.hashSecret(), hashData);
        return cfg.payUrl() + "?" + hashData + "&vnp_SecureHash=" + secureHash;
    }

    /** Verify a VNPay IPN/return callback signature and translate it to a {@link GatewayVerification}. */
    public GatewayVerification verify(Map<String, String> rawParams) {
        Map<String, String> params = new TreeMap<>(rawParams);
        String providedHash = params.remove("vnp_SecureHash");
        params.remove("vnp_SecureHashType");

        String hashData = buildEncoded(params);
        String expected = signer.hmacSha512Hex(props.vnpay().hashSecret(), hashData);
        boolean signatureValid = signer.matches(expected, providedHash == null ? "" : providedHash);

        String responseCode = rawParams.get("vnp_ResponseCode");
        String txnStatus = rawParams.get("vnp_TransactionStatus");
        boolean success = "00".equals(responseCode) && "00".equals(txnStatus);

        BigDecimal amount = null;
        if (rawParams.get("vnp_Amount") != null) {
            amount = new BigDecimal(rawParams.get("vnp_Amount")).divide(BigDecimal.valueOf(100));
        }
        return new GatewayVerification(signatureValid, success, rawParams.get("vnp_TxnRef"),
            amount, rawParams.get("vnp_TransactionNo"), responseCode);
    }

    /** URL-encode keys and values (US_ASCII) and join sorted pairs with '&'. */
    private String buildEncoded(Map<String, String> sortedParams) {
        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, String> e : sortedParams.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) {
                continue;
            }
            pairs.add(encode(e.getKey()) + "=" + encode(e.getValue()));
        }
        return String.join("&", pairs);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
