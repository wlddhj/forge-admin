package com.forge.modules.ai.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.response.PageResult;
import com.forge.common.response.Result;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.request.CreateConversationRequest;
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
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

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
    public Flux<ServerSentEvent<String>> sendMessageStream(@Valid @RequestBody ChatRequest request) {
        // 在同步线程中保存用户消息（需要 Security Context）
        aiChatService.prepareStreamChat(request);
        // 返回纯 Flux 流（不需要 Security Context）
        return aiChatService.getStreamResponse(request)
                .map(content -> ServerSentEvent.<String>builder().data(content).build());
    }

    @Operation(summary = "保存AI回复消息")
    @PostMapping("/message/save-ai")
    @PreAuthorize("hasAuthority('ai:chat:create')")
    public Result<Void> saveAiMessage(@RequestParam Long conversationId, @RequestBody String content) {
        aiChatService.saveAiMessage(conversationId, content);
        return Result.success();
    }
}