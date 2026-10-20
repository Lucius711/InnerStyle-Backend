package com.innerstyle.notification.service.impl;

import com.innerstyle.auth.entity.User;
import com.innerstyle.auth.repository.UserRepository;
import com.innerstyle.meshy.entity.enums.MeshyTaskType;
import com.innerstyle.meshy.event.MeshyTaskSucceededEvent;
import com.innerstyle.notification.config.NotificationProperties;
import com.innerstyle.notification.service.ModelNotificationService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Emails the owner (via Resend SMTP) when a long-running generation finishes, so they can leave
 * the page while Meshy works. Quick edits (remesh, rig, retexture, ...) are not emailed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailModelNotificationService implements ModelNotificationService {

    /**
     * Generations that take minutes; the user is likely no longer watching. Not FIGURE_PROTOTYPE:
     * the server goes on to the build by itself (FigurineAutoBuildListener), mail when that is done.
     */
    static final Set<MeshyTaskType> NOTIFY_TYPES = Set.of(
        MeshyTaskType.IMAGE_TO_3D, MeshyTaskType.MULTI_IMAGE_TO_3D, MeshyTaskType.TEXT_TO_3D_REFINE,
        MeshyTaskType.FIGURE_BUILD);

    static final String SUBJECT = "Mô hình 3D của bạn đã sẵn sàng";

    private final JavaMailSender mailSender;
    private final NotificationProperties props;
    private final UserRepository userRepository;

    @Override
    public void sendModelReady(MeshyTaskSucceededEvent e) {
        if (!props.enabled() || e.userId() == null || !NOTIFY_TYPES.contains(e.taskType())) {
            return;
        }
        User user = userRepository.findById(e.userId()).orElse(null);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(props.mailFrom());
            helper.setTo(user.getEmail());
            helper.setSubject(SUBJECT);
            helper.setText(html(user.getFullName(), e), true);
            mailSender.send(message);
            log.info("Model-ready email sent for task {}", e.taskId());
        } catch (Exception ex) {
            log.warn("Model-ready email for task {} failed: {}", e.taskId(), ex.getMessage());
        }
    }

    String html(String fullName, MeshyTaskSucceededEvent e) {
        // The model library, not /lab/{id}: opening the editor auto-rigs the model first (a paid,
        // minutes-long RIG task), which is not what "your model is ready" should lead to.
        String link = props.frontendBaseUrl().replaceAll("/+$", "") + "/my-3d-printing";
        String name = fullName == null || fullName.isBlank() ? "bạn" : HtmlUtils.htmlEscape(fullName);
        return """
            <div style="font-family:Inter,Arial,sans-serif;max-width:520px;margin:0 auto;padding:24px;color:#1b1630">
              <p style="margin:0;font-size:13px;color:#6d4ce6;font-weight:600">InnerStyle</p>
              <h1 style="margin:8px 0 0;font-size:20px;line-height:1.3">%s</h1>
              <p style="margin:12px 0 0;color:#5d5677">Chào %s, mô hình 3D bạn tạo đã xử lý xong. Mở để xem, chỉnh sửa hoặc đặt in.</p>
              <a href="%s" style="display:inline-block;margin-top:20px;padding:10px 18px;background:#6d4ce6;color:#fff;border-radius:999px;text-decoration:none;font-weight:600">Xem mô hình</a>
              <p style="margin:24px 0 0;font-size:12px;color:#9a94b0">Email tự động từ InnerStyle, vui lòng không trả lời.</p>
            </div>
            """.formatted(SUBJECT, name, HtmlUtils.htmlEscape(link));
    }
}
