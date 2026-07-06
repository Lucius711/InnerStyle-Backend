package com.innerstyle.auth.service;

/**
 * Sends transactional auth emails. Two implementations exist:
 * <ul>
 *   <li>{@code SmtpEmailSender} — real delivery via SMTP (Gmail); active when
 *       {@code spring.mail.username} is configured.</li>
 *   <li>{@code LoggingEmailSender} — dev fallback that logs the OTP / link so the auth flow
 *       works end-to-end without SMTP configured.</li>
 * </ul>
 */
public interface EmailSender {

    /** Send the numeric email-verification OTP issued on registration / resend. */
    void sendVerificationOtp(String toEmail, String fullName, String otp);

    void sendPasswordResetEmail(String toEmail, String fullName, String resetLink);
}
