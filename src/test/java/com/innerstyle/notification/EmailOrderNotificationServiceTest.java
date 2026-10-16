package com.innerstyle.notification;

import com.innerstyle.notification.config.NotificationProperties;
import com.innerstyle.notification.event.PrintOrderProgressEvent;
import com.innerstyle.notification.service.impl.EmailOrderNotificationService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailOrderNotificationServiceTest {

    private final UUID orderId = UUID.fromString("abcd1234-0000-0000-0000-000000000000");

    private PrintOrderProgressEvent event(String note) {
        return new PrintOrderProgressEvent(orderId, "SHIPPED", note, "GHN", "GHN123", "kh@example.com", "Kiên");
    }

    @Test
    @DisplayName("progress email: sent to the recipient with the status headline, escaped note and tracking code")
    void sendsEmail() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        var service = new EmailOrderNotificationService(sender,
            new NotificationProperties("re_key", "InnerStyle <no-reply@innerstyle.online>", "https://www.innerstyle.online/"));

        service.sendProgressUpdate(event("Đã giao <b>GHN</b>"));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(sent.capture());
        MimeMessage m = sent.getValue();
        m.saveChanges();
        assertThat(m.getSubject()).isEqualTo("Đơn in #abcd1234: Đơn hàng đã được giao cho đơn vị vận chuyển");
        assertThat(m.getAllRecipients()[0].toString()).isEqualTo("kh@example.com");
        String body = (String) m.getContent();
        assertThat(body).contains("&lt;b&gt;GHN&lt;/b&gt;").doesNotContain("<b>GHN</b>")
            .contains("GHN123").contains("https://www.innerstyle.online/print-history");
    }

    @Test
    @DisplayName("progress email: skipped while no Resend API key is configured")
    void skippedWithoutKey() {
        JavaMailSender sender = mock(JavaMailSender.class);
        var service = new EmailOrderNotificationService(sender,
            new NotificationProperties("", "InnerStyle <no-reply@innerstyle.online>", "http://localhost:5173"));

        service.sendProgressUpdate(event(null));

        verify(sender, never()).send(any(MimeMessage.class));
    }
}
