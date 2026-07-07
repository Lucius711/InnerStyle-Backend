package com.innerstyle.auth.service.impl;

import com.innerstyle.auth.config.AuthProperties;
import com.innerstyle.auth.service.EmailSender;
import com.innerstyle.common.exception.EmailDeliveryException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Real SMTP delivery (e.g. Gmail) via {@link JavaMailSender}. Sends HTML emails.
 * Instantiated by {@code EmailSenderConfig} only when {@code spring.mail.username} is set.
 */
@Slf4j
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final AuthProperties authProperties;

    public SmtpEmailSender(JavaMailSender mailSender, AuthProperties authProperties) {
        this.mailSender = mailSender;
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
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(authProperties.mailFrom(), authProperties.mailFromName());
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | MailException | UnsupportedEncodingException ex) {
            // Log the technical cause server-side; surface a stable failure code to the client.
            log.error("Failed to send email to {}: {}", toEmail, ex.getMessage(), ex);
            throw new EmailDeliveryException("auth.email.sendFailed");
        }
    }
}
