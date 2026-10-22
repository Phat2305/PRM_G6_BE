package com.example.art_course.auth.service;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class MailService {

    private final JavaMailSender mailSender;
    private final String from;
    private final String sendgridApiKey;
    private final String sendgridFrom;
    private final RestClient sendgrid;

    public MailService(JavaMailSender mailSender,
                       @Value("${app.mail.from:}") String from,
                       @Value("${app.mail.sendgrid.api-key:}") String sendgridApiKey,
                       @Value("${app.mail.sendgrid.from:}") String sendgridFrom,
                       @Value("${app.mail.sendgrid.endpoint:https://api.sendgrid.com}") String sendgridEndpoint) {
        this.mailSender = mailSender;
        this.from = from;
        this.sendgridApiKey = sendgridApiKey;
        this.sendgridFrom = sendgridFrom;
        this.sendgrid = RestClient.create(sendgridEndpoint);
    }

    // Không ném lỗi ra ngoài: người gọi phải trả cùng một kết quả dù gửi được hay không
    public void send(String to, String subject, String text) {
        try {
            if (sendgridApiKey.isBlank()) {
                sendSmtp(to, subject, text);
            } else {
                sendSendgrid(to, subject, text);
            }
        } catch (Exception e) {
            log.warn("[MAIL] Gửi mail thất bại (kiểm tra SENDGRID_API_KEY hoặc MAIL_*): {}", e.getMessage());
        }
    }

    // Render chặn cổng SMTP nên ưu tiên SendGrid (HTTPS); SMTP chỉ là đường dự phòng khi chạy local
    private void sendSendgrid(String to, String subject, String text) {
        sendgrid.post().uri("/v3/mail/send")
                .header("Authorization", "Bearer " + sendgridApiKey)
                .body(Map.of(
                        "personalizations", List.of(Map.of("to", List.of(Map.of("email", to)))),
                        "from", Map.of("email", sendgridFrom),
                        "subject", subject,
                        "content", List.of(Map.of("type", "text/plain", "value", text))))
                .retrieve()
                .toBodilessEntity();
    }

    private void sendSmtp(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (!from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}
