package com.innerstyle.auth.service.impl;

import com.innerstyle.auth.config.AuthProperties;

/**
 * Renders the subject line and HTML body for transactional auth emails. Shared by every
 * {@link com.innerstyle.auth.service.EmailSender} implementation (SMTP, Resend) so the wording
 * and markup stay identical regardless of the delivery channel.
 */
final class AuthEmailTemplates {

    private AuthEmailTemplates() {
    }

    static String otpSubject(AuthProperties authProperties) {
        return "Your " + authProperties.mailFromName() + " verification code";
    }

    static String resetSubject(AuthProperties authProperties) {
        return "Reset your " + authProperties.mailFromName() + " password";
    }

    static String otpHtml(AuthProperties authProperties, String fullName, String otp) {
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

    static String resetHtml(String fullName, String resetLink) {
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

    /** Minimal HTML escaping for interpolated user-controlled values (name / code / link). */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
