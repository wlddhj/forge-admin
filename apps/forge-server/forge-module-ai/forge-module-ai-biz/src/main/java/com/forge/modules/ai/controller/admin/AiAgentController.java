package com.forge.modules.ai.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.common.exception.BusinessException;
import com.forge.common.response.Result;
import com.forge.framework.security.utils.SecurityHelper;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.modules.ai.agent.AgentEvents;
import com.forge.modules.ai.agent.kernel.AgentGraphRunner;
import com.forge.modules.ai.entity.AiAgentConfig;
import com.forge.modules.ai.entity.AiAgentRun;
import com.forge.modules.ai.entity.AiAgentRunStep;
import com.forge.modules.ai.entity.AiAgentTodo;
import com.forge.modules.ai.service.AiAgentConfigService;
import com.forge.modules.ai.service.AiAgentRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI Agent 编排控制器：配置 / 运行历史 / 人工待办 / 对话流式执行
 */
@Slf4j
@Tag(name = "AI Agent编排")
@RestController
@RequestMapping("/ai/agent")
@RequiredArgsConstructor
public class AiAgentController {

    private final AiAgentConfigService agentConfigService;
    private final AiAgentRunService agentRunService;
    private final AgentGraphRunner graphRunner;
    private final ObjectMapper objectMapper;

    // ========== 配置 ==========

    @Operation(summary = "Agent 配置列表")
    @GetMapping("/config/list")
    @PreAuthorize("hasAuthority('ai:agent:query')")
    public Result<List<AiAgentConfig>> listConfigs(@RequestParam(required = false) Integer status) {
        return Result.success(status != null && status == 1
                ? agentConfigService.listEnabled()
                : agentConfigService.listAll());
    }

    @Operation(summary = "更新 Agent 配置")
    @PutMapping("/config/{id}")
    @PreAuthorize("hasAuthority('ai:agent:config')")
    @OperationLog(title = "AI Agent配置", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> updateConfig(@PathVariable Long id, @RequestBody AiAgentConfig config) {
        agentConfigService.update(id, config);
        return Result.success();
    }

    // ========== 运行历史 ==========

    @Operation(summary = "运行记录分页")
    @GetMapping("/run/list")
    @PreAuthorize("hasAuthority('ai:agent:query')")
    public Result<Map<String, Object>> pageRuns(@RequestParam(required = false) Long agentId,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) String triggerType,
                                                @RequestParam(required = false) Long conversationId,
                                                @RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(agentRunService.pageRuns(agentId, status, triggerType, conversationId, pageNum, pageSize));
    }

    @Operation(summary = "Agent 会话列表（按会话分组的运行记录）")
    @GetMapping("/conversations")
    @PreAuthorize("hasAuthority('ai:agent:query')")
    public Result<List<com.forge.modules.ai.dto.response.AgentConversationResponse>> listConversations() {
        return Result.success(agentRunService.listConversations());
    }

    @Operation(summary = "删除 Agent 会话（含其运行记录）")
    @DeleteMapping("/conversation/{id}")
    @PreAuthorize("hasAuthority('ai:chat:delete')")
    @OperationLog(title = "AI Agent会话", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> deleteConversation(@PathVariable Long id) {
        agentRunService.deleteConversation(id);
        return Result.success();
    }

    @Operation(summary = "运行步骤明细")
    @GetMapping("/run/{id}/steps")
    @PreAuthorize("hasAuthority('ai:agent:query')")
    public Result<List<AiAgentRunStep>> getSteps(@PathVariable Long id) {
        return Result.success(agentRunService.getSteps(id));
    }

    @Operation(summary = "运行详情（含最终回答）")
    @GetMapping("/run/{id}")
    @PreAuthorize("hasAuthority('ai:agent:query')")
    public Result<AiAgentRun> getRun(@PathVariable Long id) {
        return Result.success(agentRunService.getRun(id));
    }

    // ========== 人工待办 ==========

    @Operation(summary = "待办分页")
    @GetMapping("/todo/list")
    @PreAuthorize("hasAuthority('ai:agent:query')")
    public Result<Map<String, Object>> pageTodos(@RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = "1") int pageNum,
                                                 @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(agentRunService.pageTodos(status, pageNum, pageSize));
    }

    @Operation(summary = "待办审批通过")
    @PostMapping("/todo/{id}/approve")
    @PreAuthorize("hasAuthority('ai:agent:approve')")
    @OperationLog(title = "AI Agent待办审批", businessType = OperationLog.BusinessType.UPDATE)
    public Result<AiAgentRun> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return Result.success(agentRunService.handleTodo(id, true, body == null ? null : body.get("comment")));
    }

    @Operation(summary = "待办驳回")
    @PostMapping("/todo/{id}/reject")
    @PreAuthorize("hasAuthority('ai:agent:reject')")
    @OperationLog(title = "AI Agent待办驳回", businessType = OperationLog.BusinessType.UPDATE)
    public Result<AiAgentRun> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return Result.success(agentRunService.handleTodo(id, false, body == null ? null : body.get("comment")));
    }

    // ========== 对话流式执行 ==========

    @Operation(summary = "Agent 对话（SSE 流式，步骤事件 + 最终回答）")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('ai:agent:run')")
    public Flux<ServerSentEvent<String>> chatStream(@Valid @RequestBody AgentChatRequest request) {
        if (request.getImages() != null && request.getImages().size() > 3) {
            throw new BusinessException("图片最多 3 张");
        }
        if (request.getImages() != null) {
            for (String image : request.getImages()) {
                if (image != null && image.length() > 3 * 1024 * 1024) {
                    throw new BusinessException("单张图片超过大小限制");
                }
            }
        }
        AiAgentConfig config = agentConfigService.listEnabled().stream()
                .filter(c -> c.getAgentCode().equals(request.getAgentCode()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Agent 不存在或未启用: " + request.getAgentCode()));
        Long userId = SecurityHelper.getCurrentUserId();

        return Flux.<ServerSentEvent<String>>create(sink -> {
            try {
                graphRunner.runStream(config, request.getConversationId(), userId, "CONVERSATION",
                        request.getContent(), request.getImages(),
                        event -> sink.next(toFrame(event)));
                sink.complete();
            } catch (Exception e) {
                log.error("Agent SSE 执行异常", e);
                sink.next(toFrame(new AgentEvents.AgentEvent(AgentEvents.Type.ERROR, null, "run",
                        Map.of("message", String.valueOf(e.getMessage())))));
                sink.complete();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private ServerSentEvent<String> toFrame(AgentEvents.AgentEvent event) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("event", event.type().name());
        frame.put("node", event.node());
        frame.put("name", event.name());
        frame.put("data", event.payload());
        try {
            return ServerSentEvent.builder(objectMapper.writeValueAsString(frame)).build();
        } catch (Exception e) {
            return ServerSentEvent.builder("{\"event\":\"ERROR\"}").build();
        }
    }

    // ========== 请求 DTO ==========

    @lombok.Data
    public static class AgentChatRequest {
        @jakarta.validation.constraints.NotBlank(message = "agentCode 不能为空")
        private String agentCode;

        private Long conversationId;

        @jakarta.validation.constraints.NotBlank(message = "消息内容不能为空")
        private String content;

        private List<String> images;
    }
}
