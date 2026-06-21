package com.innerstyle.print.service;

import com.innerstyle.print.dto.response.PrintOrderInitResponse;
import com.innerstyle.print.dto.response.PrintOrderResponse;
import com.innerstyle.wallet.entity.enums.PaymentProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Places and lists 3D-print orders. Placing an order creates a PENDING order and starts a direct
 * VNPay/MoMo payment; the order becomes PAID when the gateway confirms.
 */
public interface PrintOrderService {

    PrintOrderInitResponse placeOrder(UUID userId, UUID taskId, PaymentProvider provider,
                                      String note, String clientIp);

    Page<PrintOrderResponse> list(UUID userId, Pageable pageable);
}
