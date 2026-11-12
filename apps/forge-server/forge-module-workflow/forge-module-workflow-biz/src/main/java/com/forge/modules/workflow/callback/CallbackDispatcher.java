package com.forge.modules.workflow.callback;

import com.forge.modules.workflow.entity.WfBusinessCallback;
import com.forge.modules.workflow.entity.WfCallbackRecord;
import com.forge.modules.workflow.mapper.WfCallbackRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 回调投递器：POST 签名请求 → 失败重试（0/2/10s）→ 落投递记录
 * <p>
 * 投递运行在 callbackExecutor 独立池 + 调度重试，不再占用审批事件消费线程：
 * deliver 提交即返回，各次尝试由 callbackExecutor 执行，重试等待经
 * callbackRetryScheduler.schedule 定时再提交（不在任何线程 sleep）。
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

    /**
     * timeoutMs 服务端上限：单次尝试最长 30s，防止配置异常大值让投递线程被单条回调长期占用
     */
    static final int TIMEOUT_MS_CAP = 30_000;

    private static final int TIMEOUT_MS_DEFAULT = 5000;

    private static final int RESPONSE_TRUNCATE = 2000;

    private final WfCallbackRecordMapper recordMapper;
    private final Executor callbackExecutor;
    private final ScheduledExecutorService callbackRetryScheduler;

    public CallbackDispatcher(WfCallbackRecordMapper recordMapper,
                              @Qualifier("callbackExecutor") Executor callbackExecutor,
                              @Qualifier("callbackRetryScheduler") ScheduledExecutorService callbackRetryScheduler) {
        this.recordMapper = recordMapper;
        this.callbackExecutor = callbackExecutor;
        this.callbackRetryScheduler = callbackRetryScheduler;
    }

    /**
     * 投递一次事件（含全部重试），提交后立即返回（异步执行）；任何内部异常都被吞掉（回调绝不影响主流程）
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

        // timeoutMs 一次性服务端钳制：空/非正取默认 5000，超过 30s 压到 30s，
        // 防止配置异常大值让投递线程被单条回调长期占用
        int timeoutMs = resolveTimeoutMs(callback.getTimeoutMs());
        submitAttempt(callback, payload, record, 0, timeoutMs);
    }

    /**
     * timeoutMs 解析 + 服务端钳制：空/非正取默认 5000，超过 30s 一律压到 30s
     */
    static int resolveTimeoutMs(Integer timeoutMs) {
        if (timeoutMs == null || timeoutMs <= 0) {
            return TIMEOUT_MS_DEFAULT;
        }
        return Math.min(timeoutMs, TIMEOUT_MS_CAP);
    }

    /**
     * 提交第 attempt 次尝试到 callbackExecutor；池满被 AbortPolicy 拒绝时
     * 视作一次失败，进入既有重试链（还有额度则调度重试，耗尽则落 FAILED 记录）
     */
    private void submitAttempt(WfBusinessCallback callback, String payload,
                               WfCallbackRecord record, int attempt, int timeoutMs) {
        if (attempt > 0) {
            record.setRetryCount(attempt);
        }
        try {
            callbackExecutor.execute(() -> advance(callback, payload, record, attempt, timeoutMs,
                    attemptOnce(callback, payload, timeoutMs)));
        } catch (RejectedExecutionException e) {
            log.warn("回调投递池已满，按一次失败计入重试: callback={}, event={}, attempt={}",
                    record.getCallbackName(), record.getEventType(), attempt);
            advance(callback, payload, record, attempt, timeoutMs,
                    new AttemptResult(false, null, null,
                            "RejectedExecutionException: " + e.getMessage()));
        }
    }

    /**
     * 处理一次尝试结果：成功落 SUCCESS；失败且还有额度则经调度器延迟重试（不占线程）；耗尽落 FAILED
     */
    private void advance(WfBusinessCallback callback, String payload,
                         WfCallbackRecord record, int attempt, int timeoutMs, AttemptResult result) {
        record.setHttpStatus(result.httpStatus());
        record.setResponseBody(truncate(result.body()));
        if (result.success()) {
            record.setStatus("SUCCESS");
            saveQuietly(record);
            log.info("业务回调投递成功: callback={}, event={}, instanceId={}, http={}",
                    record.getCallbackName(), record.getEventType(), record.getInstanceId(), result.httpStatus());
            return;
        }
        if (attempt + 1 < retryDelaysMillis.length) {
            callbackRetryScheduler.schedule(
                    () -> submitAttempt(callback, payload, record, attempt + 1, timeoutMs),
                    retryDelaysMillis[attempt + 1], TimeUnit.MILLISECONDS);
            return;
        }
        record.setStatus("FAILED");
        record.setErrorMsg(truncate(result.error()));
        saveQuietly(record);
        log.warn("业务回调投递失败(已重试{}次): callback={}, event={}, instanceId={}, error={}",
                retryDelaysMillis.length, record.getCallbackName(), record.getEventType(),
                record.getInstanceId(), result.error());
    }

    private AttemptResult attemptOnce(WfBusinessCallback callback, String payload, int timeoutMs) {
        try {
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
