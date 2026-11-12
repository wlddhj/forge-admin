package com.forge.modules.workflow.framework.instance;

import cn.hutool.core.util.StrUtil;
import com.aizuda.bpm.engine.entity.FlwHisInstance;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.mybatisplus.mapper.FlwHisInstanceMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwInstanceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.workflow.dto.instance.ProcessInstanceQueryRequest;
import com.forge.modules.workflow.dto.instance.ProcessInstanceResponse;
import com.forge.modules.workflow.framework.WorkflowSupport;
import com.forge.modules.workflow.mapper.WfInstanceQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流程实例查询服务（运行中/已结束/合并分页查询，自 WfProcessInstanceServiceImpl 平移）
 *
 * @author forge-admin
 */
@Component
@RequiredArgsConstructor
public class WfInstanceQueryService {

    private final FlwInstanceMapper instanceMapper;
    private final FlwHisInstanceMapper hisInstanceMapper;
    private final WfInstanceQueryMapper instanceQueryMapper;
    private final ProcessInstanceAssembler processInstanceAssembler;

    /**
     * 查询流程实例（通用方法）
     */
    public Page<ProcessInstanceResponse> queryInstances(ProcessInstanceQueryRequest request, Long startUserId) {
        // FlowLong 使用 MyBatis Plus 直接查询 flw_instance 和 flw_his_instance 表
        // 根据状态选择查询方式
        String status = request.getStatus();

        if ("finished".equals(status) || "terminated".equals(status)) {
            return queryFinishedInstances(request, startUserId, status);
        } else if ("running".equals(status)) {
            return queryRunningInstances(request, startUserId);
        } else {
            // 未指定状态：合并查询运行中和已完成的
            return queryAllInstances(request, startUserId);
        }
    }

    /**
     * 查询运行中的流程实例
     */
    private Page<ProcessInstanceResponse> queryRunningInstances(ProcessInstanceQueryRequest request, Long startUserId) {
        LambdaQueryWrapper<FlwInstance> wrapper = new LambdaQueryWrapper<>();

        // 发起人筛选
        if (startUserId != null) {
            wrapper.eq(FlwInstance::getCreateId, String.valueOf(startUserId));
        }

        // 流程定义ID筛选
        if (StrUtil.isNotBlank(request.getProcessDefinitionId())) {
            wrapper.eq(FlwInstance::getProcessId, Long.parseLong(request.getProcessDefinitionId()));
        }

        // 流程名称筛选（模糊匹配 flw_process.process_name，兼容 processName / processDefinitionName 两个字段）
        String processNameLike = buildProcessNameLike(request);
        if (processNameLike != null) {
            wrapper.apply(true,
                    "process_id IN (SELECT id FROM flw_process WHERE process_name LIKE {0})",
                    processNameLike);
        }

        // 业务Key精确筛选
        if (StrUtil.isNotBlank(request.getBusinessKey())) {
            wrapper.eq(FlwInstance::getBusinessKey, request.getBusinessKey());
        }

        // 优先级筛选
        if (request.getPriority() != null) {
            wrapper.eq(FlwInstance::getPriority, request.getPriority());
        }

        // 排序：优先级高的在前，或按创建时间
        if (Boolean.TRUE.equals(request.getSortByPriority())) {
            wrapper.orderByDesc(FlwInstance::getPriority)
                   .orderByDesc(FlwInstance::getCreateTime);
        } else {
            wrapper.orderByDesc(FlwInstance::getCreateTime);
        }

        Page<FlwInstance> pageParam = new Page<>(request.getPageNum(), request.getPageSize());
        Page<FlwInstance> instancePage = instanceMapper.selectPage(pageParam, wrapper);

        List<ProcessInstanceResponse> records = instancePage.getRecords().stream()
                .map(processInstanceAssembler::convertToResponse)
                .collect(Collectors.toList());

        Page<ProcessInstanceResponse> resultPage = new Page<>();
        resultPage.setCurrent(instancePage.getCurrent());
        resultPage.setSize(instancePage.getSize());
        resultPage.setTotal(instancePage.getTotal());
        resultPage.setRecords(records);
        return resultPage;
    }

    /**
     * 查询已完成的流程实例
     */
    private Page<ProcessInstanceResponse> queryFinishedInstances(ProcessInstanceQueryRequest request,
                                                                  Long startUserId, String status) {
        LambdaQueryWrapper<FlwHisInstance> wrapper = new LambdaQueryWrapper<>();

        // 发起人筛选
        if (startUserId != null) {
            wrapper.eq(FlwHisInstance::getCreateId, String.valueOf(startUserId));
        }

        // 流程定义ID筛选
        if (StrUtil.isNotBlank(request.getProcessDefinitionId())) {
            wrapper.eq(FlwHisInstance::getProcessId, Long.parseLong(request.getProcessDefinitionId()));
        }

        // 业务Key精确筛选
        if (StrUtil.isNotBlank(request.getBusinessKey())) {
            wrapper.eq(FlwHisInstance::getBusinessKey, request.getBusinessKey());
        }

        // 优先级筛选
        if (request.getPriority() != null) {
            wrapper.eq(FlwHisInstance::getPriority, request.getPriority());
        }

        // 流程名称筛选（模糊匹配 flw_process.process_name，兼容 processName / processDefinitionName 两个字段）
        String processNameLike = buildProcessNameLike(request);
        if (processNameLike != null) {
            wrapper.apply(true,
                    "process_id IN (SELECT id FROM flw_process WHERE process_name LIKE {0})",
                    processNameLike);
        }

        // 状态筛选（对照 FlowLong InstanceState 枚举：1=complete 2=reject 3=revoke 4=timeout 5=terminate 6=autoPass 7=autoReject）
        if ("finished".equals(status)) {
            // 已结束: 审批通过(1) + 自动通过(6)
            wrapper.in(FlwHisInstance::getInstanceState, 1, 6);
        } else if ("terminated".equals(status)) {
            // 已终止: 审批拒绝(2) + 撤销(3) + 超时(4) + 强制终止(5) + 自动拒绝(7)
            wrapper.in(FlwHisInstance::getInstanceState, 2, 3, 4, 5, 7);
        } else {
            // 全部已结束: state > 0（即排除审批中/暂停/暂存）
            wrapper.gt(FlwHisInstance::getInstanceState, 0);
        }

        // 排序：优先级高的在前，或按创建时间
        if (Boolean.TRUE.equals(request.getSortByPriority())) {
            wrapper.orderByDesc(FlwHisInstance::getPriority)
                   .orderByDesc(FlwHisInstance::getCreateTime);
        } else {
            wrapper.orderByDesc(FlwHisInstance::getCreateTime);
        }

        Page<FlwHisInstance> pageParam = new Page<>(request.getPageNum(), request.getPageSize());
        Page<FlwHisInstance> hisPage = hisInstanceMapper.selectPage(pageParam, wrapper);

        List<ProcessInstanceResponse> records = hisPage.getRecords().stream()
                .map(processInstanceAssembler::convertToHisResponse)
                .collect(Collectors.toList());

        Page<ProcessInstanceResponse> resultPage = new Page<>();
        resultPage.setCurrent(hisPage.getCurrent());
        resultPage.setSize(hisPage.getSize());
        resultPage.setTotal(hisPage.getTotal());
        resultPage.setRecords(records);
        return resultPage;
    }

    /**
     * 查询所有流程实例（不区分状态）
     * 由于 FlowLong 分开存储活动实例(flw_instance)和历史实例(flw_his_instance),
     * 需要分别查询后合并。两表 schema 一致,合并后按 startTime 倒序,内存分页。
     *
     * 注: 当前实现先全量加载再内存分页,适合单租户万级以内的流程数据。
     * 数据量超过 5w 时建议改为 SQL UNION ALL + DB 端分页。
     */
    private Page<ProcessInstanceResponse> queryAllInstances(ProcessInstanceQueryRequest request, Long startUserId) {
        // SQL 分页（UNION ALL 两侧条件下推），替代原"全量捞取+内存排序+内存分页"
        Long processDefinitionId = null;
        if (StrUtil.isNotBlank(request.getProcessDefinitionId())) {
            try {
                processDefinitionId = Long.parseLong(request.getProcessDefinitionId());
            } catch (NumberFormatException ignored) {
            }
        }
        String nameLike = buildProcessNameLike(request);

        Page<Map<String, Object>> pageParam = new Page<>(request.getPageNum(), request.getPageSize());
        List<Map<String, Object>> rows = instanceQueryMapper.selectUnionPage(pageParam,
                startUserId == null ? null : String.valueOf(startUserId),
                processDefinitionId,
                nameLike,
                StrUtil.blankToDefault(request.getBusinessKey(), null),
                request.getPriority(),
                WorkflowSupport.escapeLike(request.getTitle()),
                Boolean.TRUE.equals(request.getSortByPriority()));

        List<ProcessInstanceResponse> records = rows.stream()
                .map(processInstanceAssembler::convertUnionRowToResponse)
                .collect(Collectors.toList());

        Page<ProcessInstanceResponse> result = new Page<>(request.getPageNum(), request.getPageSize(), pageParam.getTotal());
        result.setRecords(records);
        return result;
    }

    /**
     * 构造流程名称模糊查询字符串，兼容 processName / processDefinitionName 两个入参字段。
     * 返回 null 表示无名称筛选条件。
     */
    private String buildProcessNameLike(ProcessInstanceQueryRequest request) {
        String name = StrUtil.blankToDefault(request.getProcessName(), request.getProcessDefinitionName());
        if (StrUtil.isBlank(name)) {
            return null;
        }
        return "%" + name.trim() + "%";
    }
}
