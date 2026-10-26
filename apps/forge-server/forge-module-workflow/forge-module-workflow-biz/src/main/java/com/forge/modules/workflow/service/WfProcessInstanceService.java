package com.forge.modules.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.workflow.dto.comment.ApprovalCommentResponse;
import com.forge.modules.workflow.dto.instance.AiDecisionResponse;
import com.forge.modules.workflow.dto.instance.ApprovalDetailResponse;
import com.forge.modules.workflow.dto.instance.ProcessInstanceQueryRequest;
import com.forge.modules.workflow.dto.instance.ProcessInstanceResponse;
import com.forge.modules.workflow.dto.instance.ProcessStartRequest;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 流程实例管理服务接口
 *
 * @author forge-admin
 */
public interface WfProcessInstanceService {

    /**
     * 分页查询所有流程实例
     *
     * @param request 查询参数
     * @return 分页结果
     */
    Page<ProcessInstanceResponse> pageInstance(ProcessInstanceQueryRequest request);

    /**
     * 查询当前用户的流程实例
     *
     * @param request 查询参数
     * @return 分页结果
     */
    Page<ProcessInstanceResponse> getMyInstances(ProcessInstanceQueryRequest request);

    /**
     * 根据流程实例ID获取详情
     *
     * @param processInstanceId 流程实例ID
     * @return 流程实例详情
     */
    ProcessInstanceResponse getInstanceById(String processInstanceId);

    /**
     * 发起流程
     *
     * @param request 发起请求
     */
    void startProcess(ProcessStartRequest request);

    /**
     * 取消流程实例
     *
     * @param processInstanceId 流程实例ID
     */
    void cancelProcess(String processInstanceId);

    /**
     * 唤醒流程实例（发起人将被驳回/终止/撤销/超时结束的实例恢复到可办理状态）
     *
     * @param processInstanceId 流程实例ID
     */
    void resumeInstance(String processInstanceId);

    /**
     * 获取流程实例的高亮流程图
     *
     * @param processInstanceId 流程实例ID
     * @return 流程图输入流
     */
    InputStream getInstanceDiagram(String processInstanceId);

    /**
     * 获取流程实例的审批意见列表
     *
     * @param processInstanceId 流程实例ID
     * @return 审批意见列表
     */
    List<ApprovalCommentResponse> getApprovalComments(String processInstanceId);

    /**
     * 获取流程实例的流程变量
     *
     * @param processInstanceId 流程实例ID
     * @return 流程变量Map
     */
    Map<String, Object> getProcessVariables(String processInstanceId);

    /**
     * 获取审批详情（包含审批时间线）
     *
     * @param processInstanceId 流程实例ID
     * @return 审批详情
     */
    ApprovalDetailResponse getApprovalDetail(String processInstanceId);

    /**
     * 获取实例 AI 决策记录（分支路由 + 审批决策两组，时间倒序）
     *
     * @param processInstanceId 流程实例ID
     * @return AI 决策记录
     */
    AiDecisionResponse getAiDecisions(String processInstanceId);

    /**
     * 根据业务Key查询流程实例列表
     *
     * @param businessKey 业务Key
     * @return 流程实例列表
     */
    List<ProcessInstanceResponse> getInstancesByBusinessKey(String businessKey);

    /**
     * 根据流程定义ID和业务Key精确查询流程实例
     *
     * @param processDefinitionId 流程定义ID
     * @param businessKey 业务Key
     * @return 流程实例详情（如不存在返回null）
     */
    ProcessInstanceResponse getInstanceByBusinessKey(String processDefinitionId, String businessKey);
}
