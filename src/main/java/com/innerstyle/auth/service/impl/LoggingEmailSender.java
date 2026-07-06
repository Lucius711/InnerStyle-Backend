package com.innerstyle.auth.service.impl;

import com.innerstyle.auth.service.EmailSender;
import lombok.extern.slf4j.Slf4j;

/**
 * Development email sender that logs the OTP / link instead of sending mail, so the auth flow
 * works end-to-end without SMTP configured. Selected by {@code EmailSenderConfig} when
 * {@code spring.mail.username} is blank.
 */
@Slf4j
public class LoggingEmailSender implements EmailSender {

    @Override
    public void sendVerificationOtp(String toEmail, String fullName, String otp) {
        log.info("[EMAIL] Verification OTP for {} ({}) -> {}", toEmail, fullName, otp);
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String fullName, String resetLink) {
        log.info("[EMAIL] Password reset for {} ({}) -> {}", toEmail, fullName, resetLink);
    }
}
