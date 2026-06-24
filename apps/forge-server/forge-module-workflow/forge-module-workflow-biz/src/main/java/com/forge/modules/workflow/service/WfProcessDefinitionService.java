package com.forge.modules.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.workflow.dto.definition.ProcessDefinitionQueryRequest;
import com.forge.modules.workflow.dto.definition.ProcessDefinitionResponse;
import com.forge.modules.workflow.dto.definition.ProcessDeployRequest;
import com.forge.modules.workflow.dto.definition.UserTaskNodeResponse;

import java.io.InputStream;
import java.util.List;

/**
 * 流程定义管理服务接口
 *
 * @author forge-admin
 */
public interface WfProcessDefinitionService {

    /**
     * 分页查询流程定义（仅最新版本）
     *
     * @param request 查询参数
     * @return 分页结果
     */
    Page<ProcessDefinitionResponse> pageDefinition(ProcessDefinitionQueryRequest request);

    /**
     * 根据流程定义ID获取详情
     *
     * @param processDefinitionId 流程定义ID
     * @return 流程定义详情
     */
    ProcessDefinitionResponse getDefinitionById(String processDefinitionId);

    /**
     * 部署流程定义
     *
     * @param request 部署请求
     */
    void deploy(ProcessDeployRequest request);

    /**
     * 挂起流程定义
     *
     * @param processDefinitionId 流程定义ID
     */
    void suspendDefinition(String processDefinitionId);

    /**
     * 激活流程定义
     *
     * @param processDefinitionId 流程定义ID
     */
    void activateDefinition(String processDefinitionId);

    /**
     * 删除部署（级联删除流程实例）
     *
     * @param id 流程ID
     */
    void deleteDeployment(String id);

    /**
     * 获取流程定义的 FlowLong JSON 模型
     *
     * @param processDefinitionId 流程定义ID
     * @return FlowLong JSON 模型内容
     */
    String getModelJson(String processDefinitionId);

    /**
     * 获取流程定义的流程图
     *
     * @param processDefinitionId 流程定义ID
     * @return 流程图输入流
     */
    InputStream getDiagram(String processDefinitionId);

    /**
     * 获取流程定义中需要发起人自选的用户任务节点
     *
     * @param processDefinitionId 流程定义ID
     * @return 需要发起人自选的用户任务列表
     */
    List<UserTaskNodeResponse> getStartUserSelectTasks(String processDefinitionId);
}