package com.innerstyle.notification.service.impl;

import com.innerstyle.notification.config.NotificationProperties;
import com.innerstyle.notification.event.PrintOrderProgressEvent;
import com.innerstyle.notification.service.OrderNotificationService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Emails the customer (via Resend SMTP) each time staff post a tracking update. Called by
 * {@link com.innerstyle.notification.event.PrintOrderProgressListener}; a mail failure is only logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailOrderNotificationService implements OrderNotificationService {

    /** Vietnamese headline per status (the customer base is VN; matches tracking.status.* on the site). */
    static final Map<String, String> HEADLINES = Map.of(
        "PENDING", "Đơn hàng đã được đặt, chờ thanh toán",
        "PAID", "Đã thanh toán, đơn đang chờ xử lý",
        "IN_PRODUCTION", "Mô hình của bạn đang được in",
        "SHIPPED", "Đơn hàng đã được giao cho đơn vị vận chuyển",
        "COMPLETED", "Giao hàng thành công",
        "CANCELLED", "Đơn hàng đã bị huỷ");

    private final JavaMailSender mailSender;
    private final NotificationProperties props;

    @Override
    public void sendProgressUpdate(PrintOrderProgressEvent e) {
        if (!props.enabled() || e.recipientEmail() == null || e.recipientEmail().isBlank()) {
            log.debug("Skip progress email for order {} (mail disabled or no recipient)", e.orderId());
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(props.mailFrom());
            helper.setTo(e.recipientEmail());
            helper.setSubject(subject(e));
            helper.setText(html(e), true);
            mailSender.send(message);
            log.info("Progress email sent for order {} ({})", e.orderId(), e.status());
        } catch (Exception ex) {
            log.warn("Progress email for order {} failed: {}", e.orderId(), ex.getMessage());
        }
    }

    static String shortId(PrintOrderProgressEvent e) {
        return e.orderId().toString().substring(0, 8);
    }

    static String subject(PrintOrderProgressEvent e) {
        return "Đơn in #" + shortId(e) + ": " + HEADLINES.getOrDefault(e.status(), "Có cập nhật mới");
    }

    String html(PrintOrderProgressEvent e) {
        StringBuilder rows = new StringBuilder();
        if (e.note() != null && !e.note().isBlank()) {
            rows.append("<p style=\"margin:16px 0 0;padding:12px 14px;background:#f4f1fb;border-radius:10px;color:#1b1630;white-space:pre-wrap\">")
                .append(HtmlUtils.htmlEscape(e.note())).append("</p>");
        }
        if (e.carrier() != null || e.trackingCode() != null) {
            rows.append("<p style=\"margin:16px 0 0;color:#5d5677\">");
            if (e.carrier() != null) {
                rows.append("Đơn vị vận chuyển: <b style=\"color:#1b1630\">").append(HtmlUtils.htmlEscape(e.carrier())).append("</b><br>");
            }
            if (e.trackingCode() != null) {
                rows.append("Mã vận đơn: <b style=\"color:#1b1630;font-family:monospace\">").append(HtmlUtils.htmlEscape(e.trackingCode())).append("</b>");
            }
            rows.append("</p>");
        }
        String name = e.recipientName() == null || e.recipientName().isBlank() ? "bạn" : HtmlUtils.htmlEscape(e.recipientName());
        String link = props.frontendBaseUrl().replaceAll("/+$", "") + "/print-history";
        return """
            <div style="font-family:Inter,Arial,sans-serif;max-width:520px;margin:0 auto;padding:24px;color:#1b1630">
              <p style="margin:0;font-size:13px;color:#6d4ce6;font-weight:600">InnerStyle · Đơn in #%s</p>
              <h1 style="margin:8px 0 0;font-size:20px;line-height:1.3">%s</h1>
              <p style="margin:12px 0 0;color:#5d5677">Chào %s, đơn in 3D của bạn vừa có cập nhật mới.</p>
              %s
              <a href="%s" style="display:inline-block;margin-top:20px;padding:10px 18px;background:#6d4ce6;color:#fff;border-radius:999px;text-decoration:none;font-weight:600">Theo dõi đơn hàng</a>
              <p style="margin:24px 0 0;font-size:12px;color:#9a94b0">Email tự động từ InnerStyle, vui lòng không trả lời.</p>
            </div>
            """.formatted(shortId(e), HtmlUtils.htmlEscape(HEADLINES.getOrDefault(e.status(), "Có cập nhật mới")),
                name, rows, HtmlUtils.htmlEscape(link));
    }
}
