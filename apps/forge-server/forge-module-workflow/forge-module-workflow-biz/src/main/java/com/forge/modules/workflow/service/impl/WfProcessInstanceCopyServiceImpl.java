package com.forge.modules.workflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.workflow.dto.copy.CopyQueryRequest;
import com.forge.modules.workflow.dto.copy.CopyResponse;
import com.forge.modules.workflow.entity.WfProcessDeployExt;
import com.forge.modules.workflow.entity.WfProcessInstanceCopy;
import com.forge.modules.workflow.framework.candidate.BpmTaskCandidateInvoker;
import com.forge.modules.workflow.identity.FlowableIdentityService;
import com.forge.modules.workflow.mapper.WfProcessDeployExtMapper;
import com.forge.modules.workflow.mapper.WfProcessInstanceCopyMapper;
import com.forge.modules.workflow.service.WfProcessInstanceCopyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WfProcessInstanceCopyServiceImpl implements WfProcessInstanceCopyService {

    private final WfProcessInstanceCopyMapper copyMapper;
    private final WfProcessDeployExtMapper deployExtMapper;
    private final FlowableIdentityService flowableIdentityService;
    private final RepositoryService repositoryService;
    private final HistoryService historyService;
    private final BpmTaskCandidateInvoker candidateInvoker;

    @Override
    public Page<CopyResponse> pageCopy(CopyQueryRequest request) {
        Page<WfProcessInstanceCopy> page = new Page<>(request.getPageNum(), request.getPageSize());

        LambdaQueryWrapper<WfProcessInstanceCopy> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(request.getProcessInstanceName()),
                        WfProcessInstanceCopy::getProcessInstanceName, request.getProcessInstanceName())
                .eq(request.getUserId() != null, WfProcessInstanceCopy::getUserId, request.getUserId())
                .orderByDesc(WfProcessInstanceCopy::getCreateTime);

        Page<WfProcessInstanceCopy> resultPage = copyMapper.selectPage(page, wrapper);

        // 补充流程名称为空的记录
        Map<String, String> processDefinitionNameCache = new HashMap<>();
        resultPage.getRecords().stream()
                .filter(c -> c.getProcessInstanceName() == null && c.getProcessDefinitionId() != null)
                .map(WfProcessInstanceCopy::getProcessDefinitionId)
                .distinct()
                .forEach(pdId -> {
                    try {
                        ProcessDefinition pd =
                                repositoryService.createProcessDefinitionQuery().processDefinitionId(pdId).singleResult();
                        if (pd != null && pd.getName() != null) {
                            processDefinitionNameCache.put(pdId, pd.getName());
                        }
                    } catch (Exception ignored) {}
                });

        // 批量获取用户名
        Set<Long> userIds = new HashSet<>();
        resultPage.getRecords().forEach(copy -> {
            userIds.add(copy.getStartUserId());
            userIds.add(copy.getUserId());
        });
        Map<Long, String> userNames = flowableIdentityService.getUserNames(userIds);

        Page<CopyResponse> responsePage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        responsePage.setRecords(resultPage.getRecords().stream().map(copy -> {
            CopyResponse resp = new CopyResponse();
            resp.setId(copy.getId());
            resp.setStartUserId(copy.getStartUserId());
            resp.setStartUserName(userNames.getOrDefault(copy.getStartUserId(), ""));
            String name = copy.getProcessInstanceName();
            if (name == null && copy.getProcessDefinitionId() != null) {
                name = processDefinitionNameCache.get(copy.getProcessDefinitionId());
            }
            resp.setProcessInstanceName(name);
            resp.setProcessInstanceId(copy.getProcessInstanceId());
            resp.setProcessNo(copy.getProcessNo());
            resp.setCategory(copy.getCategory());
            resp.setActivityId(copy.getActivityId());
            resp.setActivityName(copy.getActivityName());
            resp.setTaskId(copy.getTaskId());
            resp.setUserId(copy.getUserId());
            resp.setUserName(userNames.getOrDefault(copy.getUserId(), ""));
            resp.setReason(copy.getReason());
            resp.setCreateTime(copy.getCreateTime());
            return resp;
        }).collect(Collectors.toList()));

        return responsePage;
    }

    @Override
    public void autoCopyOnProcessEnd(String processInstanceId, String processDefinitionId, String reason) {
        // 1. 查询部署扩展信息，获取自动抄送配置
        WfProcessDeployExt ext = deployExtMapper.selectOne(
                new LambdaQueryWrapper<WfProcessDeployExt>()
                        .eq(WfProcessDeployExt::getProcessDefinitionId, processDefinitionId)
                        .last("LIMIT 1")
        );
        if (ext == null || ext.getAutoCopyStrategy() == null) {
            return;
        }

        // 2. 通过候选人策略计算抄送目标用户
        Set<Long> targetUserIds = candidateInvoker.calculateUsers(ext.getAutoCopyStrategy(), ext.getAutoCopyParam());
        if (targetUserIds == null || targetUserIds.isEmpty()) {
            log.info("自动抄送：未解析到目标用户，processInstanceId={}", processInstanceId);
            return;
        }

        // 3. 从历史流程实例获取信息
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (historicInstance == null) {
            log.warn("自动抄送：历史流程实例不存在，processInstanceId={}", processInstanceId);
            return;
        }

        Long startUserId = null;
        if (StrUtil.isNotBlank(historicInstance.getStartUserId())) {
            try {
                startUserId = Long.parseLong(historicInstance.getStartUserId());
            } catch (NumberFormatException ignored) {}
        }

        // 获取流程名称
        String processInstanceName = historicInstance.getName();
        if (processInstanceName == null) {
            ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(processDefinitionId).singleResult();
            if (pd != null) {
                processInstanceName = pd.getName();
            }
        }

        // 获取流程编号
        String processNo = null;
        try {
            HistoricVariableInstance var = historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .variableName("processNo")
                    .singleResult();
            if (var != null && var.getValue() != null) {
                processNo = var.getValue().toString();
            }
        } catch (Exception ignored) {}

        // 4. 创建抄送记录
        LocalDateTime now = LocalDateTime.now();
        for (Long userId : targetUserIds) {
            WfProcessInstanceCopy copy = new WfProcessInstanceCopy();
            copy.setStartUserId(startUserId);
            copy.setProcessInstanceName(processInstanceName);
            copy.setProcessInstanceId(processInstanceId);
            copy.setProcessDefinitionId(processDefinitionId);
            copy.setProcessNo(processNo);
            copy.setCategory(historicInstance.getProcessDefinitionCategory());
            copy.setActivityId("endEvent");
            copy.setActivityName("流程结束");
            copy.setUserId(userId);
            copy.setReason(reason);
            copy.setCreateTime(now);
            copy.setCreateBy(startUserId);
            copyMapper.insert(copy);
        }

        log.info("自动抄送完成：processInstanceId={}, targetUserIds={}, reason={}", processInstanceId, targetUserIds, reason);
    }
}
