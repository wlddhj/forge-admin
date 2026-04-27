package com.forge.admin.modules.quartz.service;

import com.forge.admin.modules.system.entity.SysJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 任务通知服务
 * <p>根据 sys_job.notify_config 配置，在任务执行完成后发送通知。</p>
 * <p>notifyConfig 结构示例:
 * <pre>{
 *   "notifyOnSuccess": false,
 *   "notifyOnFailure": true,
 *   "emails": ["admin@example.com"],
 *   "webhookUrl": "https://hooks.example.com/job"
 * }</pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobNotifyService {

    /**
     * 任务执行完成后发送通知
     *
     * @param job        任务信息
     * @param success    是否执行成功
     * @param duration   执行耗时(ms)
     * @param errorMessage 失败时的错误信息
     */
    public void notify(SysJob job, boolean success, long duration, String errorMessage) {
        Map<String, Object> config = job.getNotifyConfig();
        if (config == null || config.isEmpty()) {
            return;
        }

        // 判断是否需要通知
        boolean notifyOnSuccess = getBoolean(config, "notifyOnSuccess", false);
        boolean notifyOnFailure = getBoolean(config, "notifyOnFailure", true);

        if (success && !notifyOnSuccess) {
            return;
        }
        if (!success && !notifyOnFailure) {
            return;
        }

        String status = success ? "成功" : "失败";
        String message = buildMessage(job, status, duration, errorMessage);

        // 日志通知（始终执行）
        if (success) {
            log.info("[任务通知] {} - 执行{}, 耗时{}ms", job.getJobName(), status, duration);
        } else {
            log.warn("[任务通知] {} - 执行{}, 耗时{}ms, 错误: {}", job.getJobName(), status, duration, errorMessage);
        }

        // 邮件通知
        @SuppressWarnings("unchecked")
        java.util.List<String> emails = (java.util.List<String>) config.get("emails");
        if (emails != null && !emails.isEmpty()) {
            sendEmailNotification(job, message, emails);
        }

        // Webhook 通知
        String webhookUrl = (String) config.get("webhookUrl");
        if (webhookUrl != null && !webhookUrl.isEmpty()) {
            sendWebhookNotification(job, message, success, duration, webhookUrl);
        }
    }

    /**
     * 构建通知消息
     */
    private String buildMessage(SysJob job, String status, long duration, String errorMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("定时任务【").append(job.getJobName()).append("】执行").append(status);
        sb.append("，耗时").append(duration).append("ms");
        if (errorMessage != null) {
            sb.append("，错误: ").append(errorMessage);
        }
        return sb.toString();
    }

    /**
     * 发送邮件通知
     * <p>当前为日志占位实现，后续可对接邮件服务。</p>
     */
    private void sendEmailNotification(SysJob job, String message, java.util.List<String> emails) {
        // TODO: 对接邮件服务后实现实际发送
        log.info("[任务通知] 邮件通知: to={}, subject=定时任务执行通知 - {}, body={}",
                emails, job.getJobName(), message);
    }

    /**
     * 发送 Webhook 通知
     * <p>以 JSON 格式 POST 到配置的 URL。</p>
     */
    private void sendWebhookNotification(SysJob job, String message, boolean success,
                                         long duration, String webhookUrl) {
        try {
            // 使用简单的 URL 连接发送，避免引入额外 HTTP 客户端依赖
            java.net.URL url = java.net.URI.create(webhookUrl).toURL();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);

            String payload = """
                    {"jobId":%d,"jobName":"%s","status":"%s","duration":%d,"message":"%s","timestamp":"%s"}
                    """.formatted(
                    job.getId(),
                    job.getJobName(),
                    success ? "SUCCESS" : "FAIL",
                    duration,
                    message.replace("\"", "\\\""),
                    java.time.LocalDateTime.now().toString()
            );

            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            log.info("[任务通知] Webhook通知已发送: url={}, responseCode={}", webhookUrl, responseCode);
            conn.disconnect();
        } catch (Exception e) {
            log.warn("[任务通知] Webhook通知发送失败: url={}, error={}", webhookUrl, e.getMessage());
        }
    }

    private boolean getBoolean(Map<String, Object> config, String key, boolean defaultValue) {
        Object val = config.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        return defaultValue;
    }
}
