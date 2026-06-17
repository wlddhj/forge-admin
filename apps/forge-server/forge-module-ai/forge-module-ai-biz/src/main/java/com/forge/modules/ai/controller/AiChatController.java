package com.forge.modules.ai.controller;

import com.forge.common.response.Result;
import com.forge.framework.web.annotation.OperationLog;
import com.forge.modules.ai.dto.request.ChatRequest;
import com.forge.modules.ai.dto.request.CreateConversationRequest;
import com.forge.modules.ai.dto.response.ChatResponse;
import com.forge.modules.ai.dto.response.ConversationResponse;
import com.forge.modules.ai.service.AiChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    @Operation(summary = "发送聊天消息")
    @PostMapping("/send")
    @PreAuthorize("hasAuthority('ai:chat:create')")
    @OperationLog(title = "AI对话管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = aiChatService.chat(request);
        return Result.success(response);
    }

    @Operation(summary = "创建新对话")
    @PostMapping("/conversations")
    @PreAuthorize("hasAuthority('ai:chat:create')")
    @OperationLog(title = "AI对话管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<ConversationResponse> createConversation(@Valid @RequestBody CreateConversationRequest request) {
        ConversationResponse response = aiChatService.createConversation(request);
        return Result.success(response);
    }

    @Operation(summary = "获取对话列表")
    @GetMapping("/conversations")
    @PreAuthorize("hasAuthority('ai:chat:query')")
    public Result<List<ConversationResponse>> getConversations() {
        List<ConversationResponse> list = aiChatService.getConversationList();
        return Result.success(list);
    }

    @Operation(summary = "获取对话详情")
    @GetMapping("/conversations/{id}")
    @PreAuthorize("hasAuthority('ai:chat:query')")
    public Result<ConversationResponse> getConversation(@PathVariable Long id) {
        ConversationResponse response = aiChatService.getConversation(id);
        return Result.success(response);
    }

    @Operation(summary = "获取对话消息列表")
    @GetMapping("/conversations/{id}/messages")
    @PreAuthorize("hasAuthority('ai:chat:query')")
    public Result<List<ConversationResponse.MessageResponse>> getMessages(@PathVariable Long id) {
        ConversationResponse response = aiChatService.getConversation(id);
        return Result.success(response.getMessages());
    }

    @Operation(summary = "删除对话")
    @DeleteMapping("/conversations/{id}")
    @PreAuthorize("hasAuthority('ai:chat:delete')")
    @OperationLog(title = "AI对话管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> deleteConversation(@PathVariable Long id) {
        aiChatService.deleteConversation(id);
        return Result.success();
    }
}