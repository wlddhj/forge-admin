package com.forge.modules.workflow.framework.instance;

import cn.hutool.core.util.StrUtil;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.core.enums.ActorType;
import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwProcess;
import com.aizuda.bpm.engine.entity.FlwTask;
import com.aizuda.bpm.engine.entity.FlwTaskActor;
import com.aizuda.bpm.engine.ProcessService;
import com.aizuda.bpm.mybatisplus.mapper.FlwTaskActorMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.modules.workflow.dto.instance.ProcessInstanceResponse;
import com.forge.modules.workflow.entity.WfProcessExt;
import com.forge.modules.workflow.framework.title.InstanceTitleRenderer;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import com.forge.modules.workflow.mapper.WfProcessExtMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 流程实例响应装配器（活动/历史/UNION 行 → ProcessInstanceResponse，自 WfProcessInstanceServiceImpl 平移）
 *
 * @author forge-admin
 */
@Component
@RequiredArgsConstructor
public class ProcessInstanceAssembler {

    private final ProcessService processService;
    private final FlowLongIdentityService identityService;
    private final FlwTaskActorMapper taskActorMapper;
    private final QueryService queryService;
    private final WfProcessExtMapper processExtMapper;

    /**
     * 转换活动实例为响应对象
     */
    public ProcessInstanceResponse convertToResponse(FlwInstance instance) {
        ProcessInstanceResponse response = new ProcessInstanceResponse();
        response.setId(String.valueOf(instance.getId()));
        response.setProcessDefinitionId(String.valueOf(instance.getProcessId()));

        // 从流程定义获取名称
        FlwProcess process = processService.getProcessById(instance.getProcessId());
        if (process != null) {
            response.setProcessDefinitionName(process.getProcessName());
            response.setProcessDefinitionKey(process.getProcessKey());
        }

        response.setBusinessKey(instance.getBusinessKey());
        response.setStartTime(toLocalDateTime(instance.getCreateTime()));

        // 优先级信息
        response.setPriority(instance.getPriority());
        response.setPriorityName(getPriorityName(instance.getPriority()));
        response.setUrgent(instance.getUrgent());
        response.setInstanceTitle(InstanceTitleRenderer.extractTitle(instance.getVariable()));

        // 发起人信息
        if (StrUtil.isNotBlank(instance.getCreateId())) {
            response.setStartUserId(instance.getCreateId());
            try {
                Long userId = Long.parseLong(instance.getCreateId());
                response.setStartUserName(identityService.getUserName(userId));
            } catch (NumberFormatException e) {
                response.setStartUserName(instance.getCreateBy());
            }
        }

        // 当前节点信息
        response.setCurrentActivityName(instance.getCurrentNodeName());
        response.setProcessNo(instance.getInstanceNo());
        // 获取当前任务的受理人/候选人信息
        fillCurrentAssigneeInfo(response, instance.getId());

        // 扩展信息
        WfProcessExt processExt = getProcessExtByProcessId(instance.getProcessId());
        if (processExt != null) {
            response.setCategoryId(processExt.getCategoryId());
            // TODO: 设置分类名称
        }

        return response;
    }

    /**
     * 转换历史实例为响应对象
     */
    public ProcessInstanceResponse convertToHisResponse(FlwHisInstance hisInstance) {
        ProcessInstanceResponse response = new ProcessInstanceResponse();
        response.setId(String.valueOf(hisInstance.getId()));
        response.setProcessDefinitionId(String.valueOf(hisInstance.getProcessId()));

        // 从流程定义获取名称
        FlwProcess process = processService.getProcessById(hisInstance.getProcessId());
        if (process != null) {
            response.setProcessDefinitionName(process.getProcessName());
            response.setProcessDefinitionKey(process.getProcessKey());
        }

        response.setBusinessKey(hisInstance.getBusinessKey());
        response.setStartTime(toLocalDateTime(hisInstance.getCreateTime()));
        response.setEndTime(toLocalDateTime(hisInstance.getEndTime()));
        response.setDurationInMillis(hisInstance.getDuration() != null ? hisInstance.getDuration().longValue() : null);

        // 优先级信息
        response.setPriority(hisInstance.getPriority());
        response.setPriorityName(getPriorityName(hisInstance.getPriority()));
        response.setUrgent(hisInstance.getUrgent());
        response.setInstanceTitle(InstanceTitleRenderer.extractTitle(hisInstance.getVariable()));
        // 流程编号
        response.setProcessNo(hisInstance.getInstanceNo());
        // 发起人信息
        if (StrUtil.isNotBlank(hisInstance.getCreateId())) {
            response.setStartUserId(hisInstance.getCreateId());
            try {
                Long userId = Long.parseLong(hisInstance.getCreateId());
                response.setStartUserName(identityService.getUserName(userId));
            } catch (NumberFormatException e) {
                response.setStartUserName(hisInstance.getCreateBy());
            }
        }

        // 状态判断（对照 FlowLong InstanceState 枚举）
        Integer instanceState = hisInstance.getInstanceState();
        if (instanceState != null) {
            // 2=审批拒绝, 3=撤销审批, 4=超时结束, 5=强制终止, 7=自动拒绝
            response.setDeleteReason(
                    instanceState == 2 ? "审批拒绝"
                    : instanceState == 3 ? "撤销审批"
                    : instanceState == 4 ? "超时结束"
                    : instanceState == 5 ? "强制终止"
                    : instanceState == 7 ? "自动拒绝"
                    : null);
        }

        return response;
    }

    /**
     * UNION 行 → 响应对象（列名小写；时间戳由 H2/MySQL 驱动按类型返回 java.sql.Timestamp）
     */
    public ProcessInstanceResponse convertUnionRowToResponse(Map<String, Object> row) {
        ProcessInstanceResponse response = new ProcessInstanceResponse();
        response.setId(String.valueOf(row.get("id")));
        Long processId = row.get("process_id") == null ? null : Long.parseLong(String.valueOf(row.get("process_id")));
        if (processId != null) {
            response.setProcessDefinitionId(String.valueOf(processId));
            FlwProcess process = processService.getProcessById(processId);
            if (process != null) {
                response.setProcessDefinitionName(process.getProcessName());
                response.setProcessDefinitionKey(process.getProcessKey());
            }
        }
        Object createTime = row.get("create_time");
        if (createTime instanceof java.sql.Timestamp ts) {
            response.setStartTime(ts.toLocalDateTime());
        }
        Object endTime = row.get("end_time");
        if (endTime instanceof java.sql.Timestamp ts) {
            response.setEndTime(ts.toLocalDateTime());
        }
        response.setProcessNo((String) row.get("instance_no"));
        response.setCurrentActivityName((String) row.get("current_node_name"));
        String createId = (String) row.get("create_id");
        if (StrUtil.isNotBlank(createId)) {
            response.setStartUserId(createId);
            try {
                response.setStartUserName(identityService.getUserName(Long.parseLong(createId)));
            } catch (NumberFormatException e) {
                response.setStartUserName((String) row.get("create_by"));
            }
        }
        response.setBusinessKey((String) row.get("business_key"));
        Object priorityVal = row.get("priority");
        if (priorityVal != null) {
            response.setPriority(Integer.parseInt(String.valueOf(priorityVal)));
        }
        Object urgentVal = row.get("urgent");
        if (urgentVal != null) {
            int urgent = Integer.parseInt(String.valueOf(urgentVal));
            response.setUrgent(urgent);
        }
        // 流程标题（发起时按标题规则生成，存实例变量 __title）
        response.setInstanceTitle(InstanceTitleRenderer.extractTitle(
                row.get("variable") == null ? null : String.valueOf(row.get("variable"))));
        Integer state = row.get("instance_state") == null ? null
                : Integer.parseInt(String.valueOf(row.get("instance_state")));
        // 运行中实例补充当前处理人/候选人（与旧版两侧转换行为对齐）
        if (state == null || state == 0) {
            response.setCurrentActivityName((String) row.get("current_node_name"));
            try {
                fillCurrentAssigneeInfo(response, Long.parseLong(String.valueOf(row.get("id"))));
            } catch (NumberFormatException ignored) {
            }
        }
        // 历史侧状态映射（0=运行中）；终止类状态（2/3/4/5/7）映射 deleteReason 文案
        if (state != null && state > 0) {
            if (state == 2) response.setDeleteReason("审批拒绝");
            else if (state == 3) response.setDeleteReason("撤销审批");
            else if (state == 4) response.setDeleteReason("超时结束");
            else if (state == 5) response.setDeleteReason("强制终止");
            else if (state == 7) response.setDeleteReason("自动拒绝");
        }
        return response;
    }

    /**
     * 填充当前任务的受理人/候选人信息
     */
    public void fillCurrentAssigneeInfo(ProcessInstanceResponse response, Long instanceId) {
        // 获取当前实例的所有活动任务
        List<FlwTask> tasks = queryService.getTasksByInstanceId(instanceId);
        if (tasks.isEmpty()) {
            return;
        }

        List<String> assigneeNames = new ArrayList<>();
        List<String> candidateNames = new ArrayList<>();

        for (FlwTask task : tasks) {
            // 查询任务的参与者
            LambdaQueryWrapper<FlwTaskActor> actorWrapper = new LambdaQueryWrapper<>();
            actorWrapper.eq(FlwTaskActor::getTaskId, task.getId());
            List<FlwTaskActor> taskActors = taskActorMapper.selectList(actorWrapper);

            for (FlwTaskActor actor : taskActors) {
                String actorName = actor.getActorName();
                if (StrUtil.isBlank(actorName)) {
                    // 如果 actorName 为空，尝试通过 ID 获取名称
                    if (ActorType.user.eq(actor.getActorType())) {
                        // 用户类型
                        actorName = identityService.getUserName(actor.getActorId());
                    } else if (ActorType.role.eq(actor.getActorType())) {
                        // 角色/组类型
                        actorName = identityService.getGroupName(Long.parseLong(actor.getActorId()));
                    }
                }

                if (ActorType.user.eq(actor.getActorType())) {
                    // 用户类型 - 直接作为受理人或候选人
                    assigneeNames.add(actorName);
                } else if (ActorType.role.eq(actor.getActorType())) {
                    // 角色/组类型 - 作为候选组
                    candidateNames.add(actorName);
                }
            }
        }

        response.setCurrentAssigneeNames(assigneeNames);
        response.setCurrentCandidateNames(candidateNames);
    }

    /**
     * 将 java.util.Date 转换为 LocalDateTime
     */
    public LocalDateTime toLocalDateTime(java.util.Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * 获取优先级名称
     */
    private String getPriorityName(Integer priority) {
        if (priority == null || priority == 0) {
            return "普通";
        } else if (priority == 1) {
            return "高优先级";
        }
        return "未知";
    }

    /**
     * 获取流程扩展信息
     */
    private WfProcessExt getProcessExtByProcessId(Long processId) {
        LambdaQueryWrapper<WfProcessExt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfProcessExt::getProcessId, processId);
        return processExtMapper.selectOne(wrapper);
    }
}
