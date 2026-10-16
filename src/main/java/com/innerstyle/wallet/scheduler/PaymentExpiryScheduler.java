package com.innerstyle.wallet.scheduler;

import com.innerstyle.wallet.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every minute, expires payments left unpaid past {@code app.payment.order-ttl} (30 minutes by
 * default) and cancels the print orders waiting on them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpiryScheduler {

    private final PaymentService paymentService;

    @Scheduled(fixedDelayString = "${app.payment.expiry-check-interval:PT1M}", initialDelayString = "PT1M")
    public void expireStalePayments() {
        try {
            int n = paymentService.expireStale();
            if (n > 0) {
                log.info("Expired {} unpaid payment(s)", n);
            }
        } catch (Exception ex) {
            log.error("Payment expiry job failed: {}", ex.getMessage(), ex);
        }
    }
}
