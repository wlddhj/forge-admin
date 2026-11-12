package com.forge.modules.ai.controller.admin;

import tools.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.request.CreateConversationRequest;
import com.forge.modules.ai.dto.request.GenerateTitleRequest;
import com.forge.modules.ai.dto.request.SaveAiMessageRequest;
import com.forge.modules.ai.dto.request.UpdateTitleRequest;
import com.forge.modules.ai.dto.response.ConversationResponse;
import com.forge.modules.ai.dto.response.MessageResponse;
import com.forge.modules.ai.service.AiChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI对话控制器
 */
@Slf4j
@Tag(name = "AI对话管理")
@RestController
@RequestMapping("/ai/chat")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "创建新对话")
    @PostMapping("/conversation")
    @PreAuthorize("hasAuthority('ai:chat:create')")
    @OperationLog(title = "AI对话管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<ConversationResponse> createConversation(@Valid @RequestBody CreateConversationRequest request) {
        ConversationResponse response = aiChatService.createConversation(request);
        return Result.success(response);
    }

    @Operation(summary = "获取对话列表（分页）")
    @GetMapping("/conversation/list")
    @PreAuthorize("hasAuthority('ai:chat:query')")
    public Result<PageResult<ConversationResponse>> getConversationList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long modelId) {
        Page<ConversationResponse> page = aiChatService.pageConversationList(pageNum, pageSize, modelId);
        return Result.success(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @Operation(summary = "获取对话详情")
    @GetMapping("/conversation/{id}")
    @PreAuthorize("hasAuthority('ai:chat:query')")
    public Result<ConversationResponse> getConversation(@PathVariable Long id) {
        ConversationResponse response = aiChatService.getConversation(id);
        return Result.success(response);
    }

    @Operation(summary = "获取对话消息列表")
    @GetMapping("/conversation/{id}/messages")
    @PreAuthorize("hasAuthority('ai:chat:query')")
    public Result<List<MessageResponse>> getMessages(@PathVariable Long id) {
        List<MessageResponse> messages = aiChatService.getMessages(id);
        return Result.success(messages);
    }

    @Operation(summary = "AI 生成对话标题")
    @PostMapping("/conversation/{id}/generate-title")
    @PreAuthorize("hasAuthority('ai:chat:edit')")
    public Result<String> generateTitle(@PathVariable Long id, @Valid @RequestBody GenerateTitleRequest request) {
        String title = aiChatService.generateTitle(id, request.getContent());
        // 无需生成/生成失败统一返回 success(null)，前端静默处理
        return Result.success(title);
    }

    @Operation(summary = "更新对话标题")
    @PutMapping("/conversation/{id}/title")
    @PreAuthorize("hasAuthority('ai:chat:edit')")
    @OperationLog(title = "AI对话管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> updateTitle(@PathVariable Long id, @RequestBody UpdateTitleRequest request) {
        aiChatService.updateTitle(id, request.getTitle());
        return Result.success();
    }

    @Operation(summary = "删除对话")
    @DeleteMapping("/conversation/{id}")
    @PreAuthorize("hasAuthority('ai:chat:delete')")
    @OperationLog(title = "AI对话管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> deleteConversation(@PathVariable Long id) {
        aiChatService.deleteConversation(id);
        return Result.success();
    }

    @Operation(summary = "发送消息")
    @PostMapping("/message")
    @PreAuthorize("hasAuthority('ai:chat:create')")
    @OperationLog(title = "AI对话管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<MessageResponse> sendMessage(@Valid @RequestBody ChatRequest request) {
        MessageResponse response = aiChatService.sendMessage(request);
        return Result.success(response);
    }

    @Operation(summary = "发送消息（流式响应）")
    @PostMapping(value = "/message/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('ai:chat:create')")
    public SseEmitter sendMessageStream(
            @Valid @RequestBody ChatRequest request) {
        // 在同步线程中保存用户消息（需要 Security Context）
        aiChatService.prepareStreamChat(request);

        // SseEmitter 手工桥：unbounded 订阅逐帧 send。MVC ReactiveTypeHandler 的 request(1)
        // 消费与 Flux.create 组合存在唤醒丢失（攒帧），publishOn 修复又会在高频帧流引入
        // 20s+ 停顿（真机实证）；SseEmitter.send 每帧显式 flush，两条坑都绕开
        SseEmitter emitter =
                new SseEmitter(0L);
        AtomicBoolean done = new AtomicBoolean(false);
        emitter.onTimeout(() -> completeQuietly(emitter, done));
        aiChatService.getStreamResponse(request)
                .subscribe(
                        frame -> {
                            try {
                                emitter.send(frame);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        },
                        error -> {
                            // 详细原因仅记日志（error.getMessage() 可能含内部实现细节，不回传防泄露）；
                            // 错误帧经 ObjectMapper 序列化（转义天然正确），前端解析字段 error/message 不变
                            log.warn("AI 流式响应异常: {}", String.valueOf(error.getMessage()));
                            try {
                                emitter.send(objectMapper.writeValueAsString(
                                        Map.of("error", true, "message", "AI 服务异常，请稍后重试")));
                            } catch (Exception ignored) {
                                // 发送失败时仅完成
                            }
                            completeQuietly(emitter, done);
                        },
                        () -> completeQuietly(emitter, done));
        return emitter;
    }

    private void completeQuietly(SseEmitter emitter,
                                 AtomicBoolean done) {
        if (done.compareAndSet(false, true)) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // 重复完成可接受
            }
        }
    }

    @Operation(summary = "保存AI回复消息（含思考内容）")
    @PostMapping("/message/save-ai")
    @PreAuthorize("hasAuthority('ai:chat:create')")
    public Result<Void> saveAiMessage(@RequestParam Long conversationId,
                                      @Valid @RequestBody SaveAiMessageRequest request) {
        aiChatService.saveAiMessage(conversationId, request.getContent(), request.getReasoningContent());
        return Result.success();
    }
}