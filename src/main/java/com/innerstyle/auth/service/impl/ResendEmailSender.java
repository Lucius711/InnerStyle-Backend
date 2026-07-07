package com.innerstyle.auth.service.impl;

import com.innerstyle.auth.config.AuthProperties;
import com.innerstyle.auth.service.EmailSender;
import com.innerstyle.common.exception.EmailDeliveryException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Transactional email delivery via the Resend HTTP API (HTTPS, port 443). Preferred over
 * {@link SmtpEmailSender} on cloud hosts that block outbound SMTP. Instantiated by
 * {@code EmailSenderConfig} only when {@code app.resend.api-key} is set. Message wording/markup is
 * shared with the SMTP sender through {@link AuthEmailTemplates}.
 */
@Slf4j
public class ResendEmailSender implements EmailSender {

    private static final String SEND_PATH = "/emails";

    private final RestClient resendRestClient;
    private final AuthProperties authProperties;

    public ResendEmailSender(RestClient resendRestClient, AuthProperties authProperties) {
        this.resendRestClient = resendRestClient;
        this.authProperties = authProperties;
    }

    @Override
    public void sendVerificationOtp(String toEmail, String fullName, String otp) {
        send(toEmail,
            AuthEmailTemplates.otpSubject(authProperties),
            AuthEmailTemplates.otpHtml(authProperties, fullName, otp));
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String fullName, String resetLink) {
        send(toEmail,
            AuthEmailTemplates.resetSubject(authProperties),
            AuthEmailTemplates.resetHtml(fullName, resetLink));
    }

    private void send(String toEmail, String subject, String html) {
        ResendSendRequest payload = new ResendSendRequest(from(), List.of(toEmail), subject, html);
        try {
            resendRestClient.post()
                .uri(SEND_PATH)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientException ex) {
            // Log the technical cause server-side; surface a stable failure code to the client.
            log.error("Failed to send email to {} via Resend: {}", toEmail, ex.getMessage(), ex);
            throw new EmailDeliveryException("auth.email.sendFailed");
        }
    }

    /** Resend expects the sender as {@code "Display Name <address>"}. */
    private String from() {
        return authProperties.mailFromName() + " <" + authProperties.mailFrom() + ">";
    }

    /** JSON body for {@code POST /emails}. */
    private record ResendSendRequest(String from, List<String> to, String subject, String html) {
    }
}
