package com.innerstyle.auth.service.impl;

import com.innerstyle.auth.service.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Development email sender that logs the link instead of sending mail, so the auth flow
 * works end-to-end without SMTP configured. Replace with a real provider for production.
 */
@Slf4j
@Service
public class LoggingEmailSender implements EmailSender {

    @Override
    public void sendVerificationEmail(String toEmail, String fullName, String verificationLink) {
        log.info("[EMAIL] Verify email for {} ({}) -> {}", toEmail, fullName, verificationLink);
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String fullName, String resetLink) {
        log.info("[EMAIL] Password reset for {} ({}) -> {}", toEmail, fullName, resetLink);
    }
}
