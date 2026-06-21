package com.innerstyle.auth.service;

/**
 * Sends transactional auth emails. The default {@link LoggingEmailSender} just logs the link;
 * swap in an SMTP/provider implementation (e.g. spring-boot-starter-mail, SES) for production.
 */
public interface EmailSender {

    void sendVerificationEmail(String toEmail, String fullName, String verificationLink);

    void sendPasswordResetEmail(String toEmail, String fullName, String resetLink);
}
