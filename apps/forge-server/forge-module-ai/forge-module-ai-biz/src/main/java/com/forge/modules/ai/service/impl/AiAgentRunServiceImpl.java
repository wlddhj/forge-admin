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

    @Override
    public Map<String, Object> pageRuns(Long agentId, String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<AiAgentRun> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(agentId != null, AiAgentRun::getAgentId, agentId);
        wrapper.eq(status != null && !status.isBlank(), AiAgentRun::getStatus, status);
        wrapper.orderByDesc(AiAgentRun::getId);
        Page<AiAgentRun> page = runMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", page.getRecords());
        result.put("total", page.getTotal());
        return result;
    }

    @Override
    public List<AiAgentRunStep> getSteps(Long runId) {
        return runStepMapper.selectList(new LambdaQueryWrapper<AiAgentRunStep>()
                .eq(AiAgentRunStep::getRunId, runId)
                .orderByAsc(AiAgentRunStep::getId));
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
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                graphRunner.resume(todo, approved, event -> { });
            } catch (Exception e) {
                log.error("待办恢复执行失败 runId={}: {}", run.getId(), e.getMessage(), e);
            }
        });
        return run;
    }
}
