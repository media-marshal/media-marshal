package com.mediamarshal.notification;

import com.mediamarshal.model.entity.MediaTask;
import com.mediamarshal.service.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 邮件通知服务
 *
 * 仅在以下场景发送邮件（低频告警，不打扰日常使用）：
 *   1. 有任务进入 AWAITING_CONFIRMATION（需要人工处理）
 *   2. 任务处理 FAILED
 *
 * 配置项：
 *   media-marshal.notification.email.enabled  是否启用（默认 false）
 *   media-marshal.notification.email.recipient 收件人
 *   spring.mail.*                              发件服务器配置
 *
 * TODO: 支持 HTML 模板邮件（使用 Thymeleaf）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final JavaMailSender mailSender;
    private final SettingsService settingsService;

    public void notifyAwaitingConfirmation(MediaTask task) {
        if (!isEnabled()) return;
        sendEmail(
                "【Media Marshal】需要人工确认",
                String.format("文件 %s 匹配置信度较低（%.0f%%），请登录 Web UI 手动确认。",
                        task.getSourcePath(),
                        (task.getMatchConfidence() != null ? task.getMatchConfidence() : 0) * 100)
        );
    }

    public void notifyTaskFailed(MediaTask task) {
        if (!isEnabled()) return;
        sendEmail(
                "【Media Marshal】任务处理失败",
                String.format("文件 %s 处理失败。\n\n错误信息：%s",
                        task.getSourcePath(),
                        task.getErrorMessage())
        );
    }

    private void sendEmail(String subject, String text) {
        String recipient = settingsService.get("notification.email.recipient", "");
        if (recipient.isBlank()) {
            log.warn("Email notification enabled but recipient not configured");
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipient);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("Email sent to {}: {}", recipient, subject);
        } catch (Exception e) {
            log.error("Failed to send email notification", e);
        }
    }

    private boolean isEnabled() {
        return Boolean.parseBoolean(settingsService.get("notification.email.enabled", "false"));
    }
}
