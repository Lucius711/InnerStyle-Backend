package com.innerstyle.auth.config;

import com.innerstyle.auth.service.EmailSender;
import com.innerstyle.auth.service.impl.LoggingEmailSender;
import com.innerstyle.auth.service.impl.SmtpEmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;

/**
 * Chooses the {@link EmailSender} implementation deterministically at startup:
 * real SMTP delivery when {@code spring.mail.username} is configured, otherwise a logging
 * fallback so local dev works without SMTP. Using a factory (rather than bean conditions)
 * avoids the empty-string ambiguity of {@code @ConditionalOnProperty}.
 */
@Slf4j
@Configuration
public class EmailSenderConfig {

    @Bean
    public EmailSender emailSender(@Value("${spring.mail.username:}") String mailUsername,
                                   ObjectProvider<JavaMailSender> mailSenderProvider,
                                   AuthProperties authProperties) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (StringUtils.hasText(mailUsername) && mailSender != null) {
            log.info("EmailSender: SMTP delivery enabled (sender={})", authProperties.mailFrom());
            return new SmtpEmailSender(mailSender, authProperties);
        }
        log.warn("EmailSender: spring.mail.username not set — using LoggingEmailSender "
            + "(verification OTP will be printed to the log, not emailed)");
        return new LoggingEmailSender();
    }
}
