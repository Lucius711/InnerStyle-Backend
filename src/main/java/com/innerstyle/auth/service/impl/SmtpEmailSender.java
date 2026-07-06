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
        String subject = "Your " + authProperties.mailFromName() + " verification code";
        String html = buildOtpHtml(fullName, otp);
        send(toEmail, subject, html);
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String fullName, String resetLink) {
        String subject = "Reset your " + authProperties.mailFromName() + " password";
        String html = buildResetHtml(fullName, resetLink);
        send(toEmail, subject, html);
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

    private String buildOtpHtml(String fullName, String otp) {
        long minutes = authProperties.otpTtl().toMinutes();
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:480px;margin:0 auto;"
            + "padding:24px;color:#111\">"
            + "<h2 style=\"margin:0 0 16px\">Verify your email</h2>"
            + "<p>Hi " + escape(fullName) + ",</p>"
            + "<p>Use the code below to verify your email address:</p>"
            + "<div style=\"font-size:32px;font-weight:700;letter-spacing:8px;text-align:center;"
            + "padding:16px 0;color:#4f46e5\">" + escape(otp) + "</div>"
            + "<p style=\"color:#666;font-size:14px\">This code expires in " + minutes
            + " minutes. If you didn't create an account, you can ignore this email.</p>"
            + "<p style=\"color:#999;font-size:12px;margin-top:24px\">"
            + escape(authProperties.mailFromName()) + "</p>"
            + "</div>";
    }

    private String buildResetHtml(String fullName, String resetLink) {
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:480px;margin:0 auto;"
            + "padding:24px;color:#111\">"
            + "<h2 style=\"margin:0 0 16px\">Reset your password</h2>"
            + "<p>Hi " + escape(fullName) + ",</p>"
            + "<p>Click the button below to choose a new password:</p>"
            + "<p style=\"text-align:center;padding:16px 0\">"
            + "<a href=\"" + escape(resetLink) + "\" style=\"background:#4f46e5;color:#fff;"
            + "text-decoration:none;padding:12px 24px;border-radius:8px;display:inline-block\">"
            + "Reset password</a></p>"
            + "<p style=\"color:#666;font-size:14px\">If you didn't request this, you can ignore this email.</p>"
            + "</div>";
    }

    /** Minimal HTML escaping for interpolated user-controlled values (name / code). */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
