package com.forge.admin.modules.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.modules.workflow.dto.comment.ApprovalCommentResponse;
import com.forge.admin.modules.workflow.dto.instance.ApprovalDetailResponse;
import com.forge.admin.modules.workflow.dto.instance.ProcessInstanceQueryRequest;
import com.forge.admin.modules.workflow.dto.instance.ProcessInstanceResponse;
import com.forge.admin.modules.workflow.dto.instance.ProcessStartRequest;

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
}
