package com.forge.modules.workflow.callback;

import com.forge.modules.workflow.entity.WfBusinessCallback;
import com.forge.modules.workflow.entity.WfCallbackRecord;
import com.forge.modules.workflow.mapper.WfCallbackRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

/**
 * 回调投递器：POST 签名请求 → 失败重试（0/2/10s）→ 落投递记录
 * <p>
 * 运行在 workflowExecutor 异步线程，sleep 重试不占用 web 线程。
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class CallbackDispatcher {

    /**
     * 重试间隔（毫秒），首次投递 + N 次重试共 attempts = delays.length 次
     */
    private final long[] retryDelaysMillis = {0, 2000, 10000};

    private static final int RESPONSE_TRUNCATE = 2000;

    private final WfCallbackRecordMapper recordMapper;

    public CallbackDispatcher(WfCallbackRecordMapper recordMapper) {
        this.recordMapper = recordMapper;
    }

    /**
     * 投递一次事件（含全部重试），返回终态记录；任何内部异常都被吞掉（回调绝不影响主流程）
     */
    public void deliver(WfBusinessCallback callback, String eventType, String processKey,
                        String businessKey, Long instanceId, Long taskId, String nodeKey,
                        String payload) {
        WfCallbackRecord record = new WfCallbackRecord();
        record.setCallbackId(callback.getId());
        record.setCallbackName(callback.getName());
        record.setEventType(eventType);
        record.setProcessKey(processKey);
        record.setBusinessKey(businessKey);
        record.setInstanceId(instanceId);
        record.setTaskId(taskId);
        record.setNodeKey(nodeKey);
        record.setPayload(payload);
        record.setRetryCount(0);
        record.setCreateTime(LocalDateTime.now());

        String lastError = null;
        Integer lastStatus = null;
        String lastBody = null;
        for (int i = 0; i < retryDelaysMillis.length; i++) {
            if (i > 0) {
                record.setRetryCount(i);
            }
            try {
                if (retryDelaysMillis[i] > 0) {
                    Thread.sleep(retryDelaysMillis[i]);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
            AttemptResult result = attemptOnce(callback, payload);
            lastStatus = result.httpStatus;
            lastBody = truncate(result.body);
            lastError = result.error;
            if (result.success) {
                record.setStatus("SUCCESS");
                record.setHttpStatus(lastStatus);
                record.setResponseBody(lastBody);
                saveQuietly(record);
                log.info("业务回调投递成功: callback={}, event={}, instanceId={}, http={}",
                        callback.getName(), eventType, instanceId, lastStatus);
                return;
            }
        }
        record.setStatus("FAILED");
        record.setHttpStatus(lastStatus);
        record.setResponseBody(lastBody);
        record.setErrorMsg(truncate(lastError));
        saveQuietly(record);
        log.warn("业务回调投递失败(已重试{}次): callback={}, event={}, instanceId={}, error={}",
                retryDelaysMillis.length, callback.getName(), eventType, instanceId, lastError);
    }

    private AttemptResult attemptOnce(WfBusinessCallback callback, String payload) {
        try {
            int timeoutMs = callback.getTimeoutMs() != null && callback.getTimeoutMs() > 0
                    ? callback.getTimeoutMs() : 5000;
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(timeoutMs);
            factory.setReadTimeout(timeoutMs);
            RestTemplate restTemplate = new RestTemplate(factory);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String timestamp = String.valueOf(System.currentTimeMillis());
            headers.set("X-Forge-Timestamp", timestamp);
            String signature = CallbackSigner.sign(callback.getSecret(), timestamp, payload);
            if (signature != null) {
                headers.set("X-Forge-Signature", signature);
            }

            ResponseEntity<String> response = restTemplate.postForEntity(
                    callback.getUrl(), new HttpEntity<>(payload, headers), String.class);
            boolean success = response.getStatusCode().is2xxSuccessful();
            return new AttemptResult(success, response.getStatusCode().value(),
                    response.getBody(), success ? null : "HTTP " + response.getStatusCode().value());
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            // 4xx/5xx 默认被 RestTemplate 抛异常——保留真实状态码与响应体
            return new AttemptResult(false, e.getStatusCode().value(),
                    e.getResponseBodyAsString(), "HTTP " + e.getStatusCode().value());
        } catch (Exception e) {
            return new AttemptResult(false, null, null, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void saveQuietly(WfCallbackRecord record) {
        try {
            recordMapper.insert(record);
        } catch (Exception e) {
            log.error("回调投递记录落库失败: callback={}, event={}", record.getCallbackName(), record.getEventType(), e);
        }
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > RESPONSE_TRUNCATE ? s.substring(0, RESPONSE_TRUNCATE) : s;
    }

    private record AttemptResult(boolean success, Integer httpStatus, String body, String error) {
    }
}
