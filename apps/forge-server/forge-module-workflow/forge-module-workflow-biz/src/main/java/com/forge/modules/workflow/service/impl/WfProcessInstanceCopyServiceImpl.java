package com.forge.modules.workflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.enums.TaskType;
import com.aizuda.bpm.engine.entity.*;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisTaskActorMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.common.exception.BusinessException;
import com.forge.common.utils.SecurityUtils;
import com.forge.common.utils.UserContext;
import com.forge.modules.workflow.entity.WfProcessExt;
import com.forge.modules.workflow.framework.FlowCreatorFactory;
import com.forge.modules.workflow.framework.copy.AutoCopyUserResolver;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import com.forge.modules.workflow.mapper.WfProcessExtMapper;
import com.forge.modules.workflow.service.WfProcessInstanceCopyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 流程抄送服务实现
 *
 * 抄送记录通过引擎 createCcTask 落入 flw_his_task(taskType=cc)，
 * "抄送我的"查询链路（weight=6）天然可见；同实例同人去重由本层负责（引擎不去重）。
 *
 * @author forge-admin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfProcessInstanceCopyServiceImpl implements WfProcessInstanceCopyService {

    private static final String[] ADMIN_ROLE_CODES = {"admin", "workflow_admin"};

    private final FlowLongEngine flowLongEngine;
    private final QueryService queryService;
    private final WfProcessExtMapper processExtMapper;
    private final AutoCopyUserResolver autoCopyUserResolver;
    private final FlowLongIdentityService identityService;
    private final FlwHisTaskActorMapper hisTaskActorMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void autoCopyOnProcessEnd(String processInstanceId, String processDefinitionId, String reason) {
        try {
            Long instanceId = Long.parseLong(processInstanceId);
            Long processId = StrUtil.isBlank(processDefinitionId) ? null : Long.parseLong(processDefinitionId);

            WfProcessExt processExt = getProcessExt(processId);
            if (processExt == null || processExt.getAutoCopyStrategy() == null) {
                log.debug("流程结束自动抄送：未配置自动抄送策略: instanceId={}", instanceId);
                return;
            }

            InstanceSnapshot snapshot = loadSnapshot(instanceId);
            Set<Long> copyUserIds = autoCopyUserResolver.resolveUsers(
                    processExt.getAutoCopyStrategy(), processExt.getAutoCopyParam(),
                    snapshot.startUserId(), snapshot.variables());
            if (copyUserIds.isEmpty()) {
                log.warn("流程结束自动抄送：策略解析结果为空: instanceId={}, strategy={}",
                        instanceId, processExt.getAutoCopyStrategy());
                return;
            }

            FlwHisTask anchor = queryService.getStartTaskByInstanceId(instanceId);
            if (anchor == null) {
                log.warn("流程结束自动抄送：实例无发起任务可作抄送锚点: instanceId={}", instanceId);
                return;
            }

            int copied = createCcRecords(instanceId, anchor, copyUserIds,
                    snapshot.startUserId(), StrUtil.blankToDefault(reason, "流程结束自动抄送"));
            log.info("流程结束自动抄送完成: instanceId={}, strategy={}, resolved={}, newlyCopied={}",
                    instanceId, processExt.getAutoCopyStrategy(), copyUserIds.size(), copied);
        } catch (Exception e) {
            log.error("流程结束自动抄送异常: instanceId={}", processInstanceId, e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void manualCopy(String processInstanceId, List<Long> copyUserIds, String reason) {
        if (copyUserIds == null || copyUserIds.isEmpty()) {
            throw new BusinessException(400, "抄送用户列表不能为空");
        }
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }

        Long instanceId;
        try {
            instanceId = Long.parseLong(processInstanceId);
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "流程实例ID格式错误");
        }

        FlwInstance instance = queryService.getInstance(instanceId);
        if (instance == null) {
            throw new BusinessException(400, "流程已结束，无法手动抄送");
        }

        // 仅发起人或工作流管理员可对实例手动抄送
        boolean isInitiator = String.valueOf(currentUserId).equals(instance.getCreateId());
        if (!isInitiator && !hasAdminRole()) {
            throw new BusinessException(403, "只有流程发起人或工作流管理员才能手动抄送");
        }

        // 锚点：优先当前活动审批任务，无则回退发起任务
        FlwTask anchor = queryService.getTasksByInstanceId(instanceId).stream()
                .filter(t -> TaskType.approval.eq(t.getTaskType()))
                .findFirst()
                .orElse(null);
        if (anchor == null) {
            FlwHisTask startTask = queryService.getStartTaskByInstanceId(instanceId);
            if (startTask == null) {
                throw new BusinessException(400, "流程实例无任务可作抄送锚点");
            }
            anchor = startTask;
        }

        Long startUserId = parseUserId(instance.getCreateId());
        int copied = createCcRecords(instanceId, anchor, new LinkedHashSet<>(copyUserIds),
                startUserId, StrUtil.blankToDefault(reason, "手动抄送"));
        if (copied == 0) {
            throw new BusinessException(400, "所选用户均已被抄送，无需重复抄送");
        }
        log.info("实例手动抄送完成: instanceId={}, operator={}, copied={}", instanceId, currentUserId, copied);
    }

    // ========== 私有方法 ==========

    /**
     * 生成抄送记录（同实例同人去重后），返回实际新建条数
     */
    private int createCcRecords(Long instanceId, FlwTask anchor, Set<Long> copyUserIds,
                                Long startUserId, String reason) {
        Set<String> copiedActorIds = existingCcActorIds(instanceId);
        List<FlwTaskActor> actors = copyUserIds.stream()
                .filter(userId -> !copiedActorIds.contains(String.valueOf(userId)))
                .map(userId -> FlwTaskActor.ofUser(FlowCreatorFactory.currentTenantId(),
                        String.valueOf(userId), identityService.getUserName(userId)))
                .collect(Collectors.toList());
        if (actors.isEmpty()) {
            return 0;
        }

        FlowCreator creator = startUserId != null
                ? FlowCreatorFactory.user(startUserId, identityService.getUserName(startUserId))
                : FlowCreatorFactory.system("系统");
        flowLongEngine.createCcTask(anchor, actors, creator);
        return actors.size();
    }

    private Set<String> existingCcActorIds(Long instanceId) {
        LambdaQueryWrapper<FlwHisTaskActor> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlwHisTaskActor::getInstanceId, instanceId);
        wrapper.eq(FlwHisTaskActor::getWeight, 6); // 抄送人标识
        return hisTaskActorMapper.selectList(wrapper).stream()
                .map(FlwHisTaskActor::getActorId)
                .collect(Collectors.toSet());
    }

    private WfProcessExt getProcessExt(Long processId) {
        if (processId == null) {
            return null;
        }
        LambdaQueryWrapper<WfProcessExt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfProcessExt::getProcessId, processId);
        wrapper.orderByDesc(WfProcessExt::getId);
        wrapper.last("LIMIT 1");
        return processExtMapper.selectOne(wrapper);
    }

    private InstanceSnapshot loadSnapshot(Long instanceId) {
        FlwInstance active = queryService.getInstance(instanceId);
        FlwHisInstance his = active == null ? queryService.getHistInstance(instanceId) : null;
        String createId = active != null ? active.getCreateId()
                : his != null ? his.getCreateId() : null;
        String variable = active != null ? active.getVariable()
                : his != null ? his.getVariable() : null;

        Map<String, Object> variables = new HashMap<>();
        if (StrUtil.isNotBlank(variable)) {
            try {
                variables = objectMapper.readValue(variable, Map.class);
            } catch (Exception e) {
                log.warn("解析流程变量失败: instanceId={}", instanceId, e);
            }
        }
        return new InstanceSnapshot(parseUserId(createId), variables);
    }

    private Long parseUserId(String userId) {
        if (StrUtil.isBlank(userId)) {
            return null;
        }
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean hasAdminRole() {
        UserContext context = UserContext.get();
        if (context == null) {
            return false;
        }
        if (context.isAdmin()) {
            return true;
        }
        if (context.getRoles() == null) {
            return false;
        }
        return context.getRoles().stream()
                .map(role -> role.getRoleCode() == null ? "" : role.getRoleCode().toLowerCase())
                .anyMatch(code -> Arrays.stream(ADMIN_ROLE_CODES).anyMatch(code::equals));
    }

    private record InstanceSnapshot(Long startUserId, Map<String, Object> variables) {
    }
}
