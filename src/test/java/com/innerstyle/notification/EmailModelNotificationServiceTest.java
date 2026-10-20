package com.innerstyle.notification;

import com.innerstyle.auth.entity.User;
import com.innerstyle.auth.repository.UserRepository;
import com.innerstyle.meshy.entity.enums.MeshyTaskType;
import com.innerstyle.meshy.event.MeshyTaskSucceededEvent;
import com.innerstyle.notification.config.NotificationProperties;
import com.innerstyle.notification.service.impl.EmailModelNotificationService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailModelNotificationServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final UserRepository users = mock(UserRepository.class);
    private final EmailModelNotificationService service = new EmailModelNotificationService(sender,
        new NotificationProperties("re_key", "InnerStyle <no-reply@innerstyle.online>", "https://www.innerstyle.online/"),
        users);

    @Test
    @DisplayName("model-ready email: sent for a long generation, skipped for quick edits")
    void emailsOnlyLongGenerations() {
        User user = mock(User.class);
        when(user.getEmail()).thenReturn("kh@example.com");
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        service.sendModelReady(new MeshyTaskSucceededEvent(UUID.randomUUID(), MeshyTaskType.REMESH, userId));
        verify(sender, never()).send(any(MimeMessage.class));

        service.sendModelReady(new MeshyTaskSucceededEvent(UUID.randomUUID(), MeshyTaskType.IMAGE_TO_3D, userId));
        verify(sender).send(any(MimeMessage.class));
    }
}
