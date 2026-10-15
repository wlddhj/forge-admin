package com.forge.modules.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.exception.BusinessException;
import com.forge.modules.ai.agent.kernel.AgentGraphRunner;
import com.forge.modules.ai.entity.AiAgentRun;
import com.forge.modules.ai.entity.AiAgentRunStep;
import com.forge.modules.ai.entity.AiAgentTodo;
import com.forge.modules.ai.mapper.AiAgentRunMapper;
import com.forge.modules.ai.mapper.AiAgentRunStepMapper;
import com.forge.modules.ai.mapper.AiAgentTodoMapper;
import com.forge.modules.ai.service.AiAgentRunService;
import com.forge.framework.security.utils.SecurityHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentRunServiceImpl implements AiAgentRunService {

    private final AiAgentRunMapper runMapper;
    private final AiAgentRunStepMapper runStepMapper;
    private final AiAgentTodoMapper todoMapper;
    private final AgentGraphRunner graphRunner;
    private final com.forge.framework.web.websocket.NotificationService notificationService;
    private final com.forge.modules.ai.service.AiChatService aiChatService;

    @Override
    public Map<String, Object> pageRuns(Long agentId, String status, String triggerType, Long conversationId,
                                         int pageNum, int pageSize) {
        LambdaQueryWrapper<AiAgentRun> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(agentId != null, AiAgentRun::getAgentId, agentId);
        wrapper.eq(status != null && !status.isBlank(), AiAgentRun::getStatus, status);
        wrapper.eq(triggerType != null && !triggerType.isBlank(), AiAgentRun::getTriggerType, triggerType);
        wrapper.eq(conversationId != null, AiAgentRun::getConversationId, conversationId);
        wrapper.orderByDesc(AiAgentRun::getId);
        Page<AiAgentRun> page = runMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", page.getRecords());
        result.put("total", page.getTotal());
        return result;
    }

    @Override
    public List<com.forge.modules.ai.dto.response.AgentConversationResponse> listConversations() {
        return runMapper.selectAgentConversations(SecurityHelper.getCurrentUserId());
    }

    @Override
    @Transactional
    public void deleteConversation(Long conversationId) {
        Long userId = SecurityHelper.getCurrentUserId();
        LambdaQueryWrapper<AiAgentRun> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiAgentRun::getConversationId, conversationId)
                .eq(AiAgentRun::getUserId, userId)
                .eq(AiAgentRun::getTriggerType, "CONVERSATION");
        long count = runMapper.selectCount(wrapper);
        if (count == 0) {
            throw new BusinessException("Agent 会话不存在或无权删除");
        }
        runMapper.delete(wrapper);
        aiChatService.deleteConversation(conversationId);
    }

    @Override
    public List<AiAgentRunStep> getSteps(Long runId) {
        return runStepMapper.selectList(new LambdaQueryWrapper<AiAgentRunStep>()
                .eq(AiAgentRunStep::getRunId, runId)
                .orderByAsc(AiAgentRunStep::getId));
    }

    @Override
    public AiAgentRun getRun(Long id) {
        AiAgentRun run = runMapper.selectById(id);
        if (run == null) {
            throw new BusinessException("运行记录不存在");
        }
        return run;
    }

    @Override
    public Map<String, Object> pageTodos(String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<AiAgentTodo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null && !status.isBlank(), AiAgentTodo::getStatus, status);
        wrapper.orderByDesc(AiAgentTodo::getId);
        Page<AiAgentTodo> page = todoMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", page.getRecords());
        result.put("total", page.getTotal());
        return result;
    }

    @Override
    @Transactional
    public AiAgentRun handleTodo(Long todoId, boolean approved, String comment) {
        AiAgentTodo todo = todoMapper.selectById(todoId);
        if (todo == null) {
            throw new BusinessException("待办不存在");
        }
        if (!AiAgentTodo.STATUS_PENDING.equals(todo.getStatus())) {
            throw new BusinessException("待办已处理");
        }
        AiAgentRun run = runMapper.selectById(todo.getRunId());
        if (run == null || !AiAgentRun.STATUS_WAITING_APPROVAL.equals(run.getStatus())) {
            throw new BusinessException("关联运行不在待确认状态");
        }

        todo.setStatus(approved ? AiAgentTodo.STATUS_APPROVED : AiAgentTodo.STATUS_REJECTED);
        todo.setHandlerId(SecurityHelper.getCurrentUserId());
        todo.setHandlerName(SecurityHelper.getCurrentUsername());
        todo.setHandleComment(comment);
        todo.setHandleTime(LocalDateTime.now());
        todoMapper.updateById(todo);

        // 异步恢复图执行：图剩余流程含多次 LLM 调用（可达数十秒），同步等待会阻塞请求直至超时
        run.setStatus(AiAgentRun.STATUS_RUNNING);
        runMapper.updateById(run);
        Long runUserId = run.getUserId();
        Long runId = run.getId();
        // 实时推送"审批已通过、恢复执行中"（对话页秒级感知，替代轮询发现；前端不弹通知中心）
        notifyApprovalResumed(runUserId, run.getId(), approved);
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                AiAgentRun finished = graphRunner.resume(todo, approved, event -> { });
                notifyRunFinished(runUserId, finished);
            } catch (Exception e) {
                log.error("待办恢复执行失败 runId={}: {}", runId, e.getMessage(), e);
            }
        });
        return run;
    }

    /**
     * 审批处理后立即推送恢复状态（对话页消费：AGENT_RUN_STATUS 仅页面态，不弹通知中心）
     */
    private void notifyApprovalResumed(Long userId, Long runId, boolean approved) {
        try {
            com.forge.framework.web.websocket.NotificationMessage message =
                    new com.forge.framework.web.websocket.NotificationMessage();
            message.setType("AGENT_RUN_STATUS");
            message.setTitle("Agent 运行 #" + runId + (approved ? " 审批已通过" : " 审批已驳回"));
            message.setContent(approved ? "正在继续执行…" : "运行已按驳回结论收尾");
            message.setRelatedId(runId);
            message.setTimestamp(System.currentTimeMillis());
            notificationService.sendToUser(userId, message);
        } catch (Exception e) {
            log.warn("审批状态推送失败 runId={}: {}", runId, e.getMessage());
        }
    }

    /**
     * 恢复完成后向发起用户推送站内通知（对话页 SSE 已断开，靠通知 + 运行历史/轮询获取结果）
     */
    private void notifyRunFinished(Long userId, AiAgentRun run) {
        try {
            com.forge.framework.web.websocket.NotificationMessage message =
                    new com.forge.framework.web.websocket.NotificationMessage();
            message.setType("AGENT_RUN");
            message.setTitle("Agent 运行 #" + run.getId() + (AiAgentRun.STATUS_SUCCEEDED.equals(run.getStatus())
                    ? " 已完成" : " 失败"));
            message.setContent(run.getFinalAnswer() != null && run.getFinalAnswer().length() > 200
                    ? run.getFinalAnswer().substring(0, 200) + "…"
                    : String.valueOf(run.getFinalAnswer() == null ? run.getErrorMsg() : run.getFinalAnswer()));
            message.setRelatedId(run.getId());
            message.setTimestamp(System.currentTimeMillis());
            notificationService.sendToUser(userId, message);
        } catch (Exception e) {
            log.warn("Agent 运行完成通知推送失败 runId={}: {}", run.getId(), e.getMessage());
        }
    }
}
