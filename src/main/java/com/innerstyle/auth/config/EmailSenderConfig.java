package com.innerstyle.auth.config;

import com.innerstyle.auth.service.EmailSender;
import com.innerstyle.auth.service.impl.LoggingEmailSender;
import com.innerstyle.auth.service.impl.ResendEmailSender;
import com.innerstyle.auth.service.impl.SmtpEmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Chooses the {@link EmailSender} implementation deterministically at startup, in priority order:
 * <ol>
 *   <li>Resend HTTP API when {@code app.resend.api-key} is set — works even on hosts that block
 *       outbound SMTP (e.g. Railway on non-Pro plans).</li>
 *   <li>SMTP delivery when {@code spring.mail.username} is configured.</li>
 *   <li>A logging fallback so local dev works without any mail provider.</li>
 * </ol>
 * Using a factory (rather than bean conditions) avoids the empty-string ambiguity of
 * {@code @ConditionalOnProperty}.
 */
@Slf4j
@Configuration
public class EmailSenderConfig {

    @Bean
    public EmailSender emailSender(@Value("${spring.mail.username:}") String mailUsername,
                                   ObjectProvider<JavaMailSender> mailSenderProvider,
                                   @Qualifier(ResendClientConfig.RESEND_REST_CLIENT)
                                   RestClient resendRestClient,
                                   ResendProperties resendProperties,
                                   AuthProperties authProperties) {
        if (resendProperties.hasApiKey()) {
            log.info("EmailSender: Resend HTTP API delivery enabled (sender={})",
                authProperties.mailFrom());
            return new ResendEmailSender(resendRestClient, authProperties);
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (StringUtils.hasText(mailUsername) && mailSender != null) {
            log.info("EmailSender: SMTP delivery enabled (sender={})", authProperties.mailFrom());
            return new SmtpEmailSender(mailSender, authProperties);
        }
        log.warn("EmailSender: no mail provider configured (app.resend.api-key / spring.mail.username "
            + "both blank) — using LoggingEmailSender (verification OTP printed to the log, not emailed)");
        return new LoggingEmailSender();
    }
}
