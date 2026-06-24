package com.forge.modules.workflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.aizuda.bpm.engine.FlowLongEngine;
import com.aizuda.bpm.engine.ProcessService;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.core.enums.FlowState;
import com.aizuda.bpm.engine.core.enums.NodeSetType;
import com.aizuda.bpm.engine.core.enums.TaskType;
import com.aizuda.bpm.engine.entity.FlwInstance;
import com.aizuda.bpm.engine.entity.FlwProcess;
import com.aizuda.bpm.mybatisplus.mapper.FlwInstanceMapper;
import com.aizuda.bpm.mybatisplus.mapper.FlwProcessMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.common.exception.BusinessException;
import com.forge.common.utils.SecurityUtils;
import com.forge.modules.workflow.dto.definition.ProcessDefinitionQueryRequest;
import com.forge.modules.workflow.dto.definition.ProcessDefinitionResponse;
import com.forge.modules.workflow.dto.definition.ProcessDeployRequest;
import com.forge.modules.workflow.dto.definition.UserTaskNodeResponse;
import com.forge.modules.workflow.entity.WfCategory;
import com.forge.modules.workflow.entity.WfProcessExt;
import com.forge.modules.workflow.framework.diagram.FlowLongDiagramGenerator;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import com.forge.modules.workflow.mapper.WfCategoryMapper;
import com.forge.modules.workflow.mapper.WfProcessExtMapper;
import com.forge.modules.workflow.service.WfProcessDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 流程定义管理服务实现 - FlowLong 版本
 *
 * @author forge-admin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfProcessDefinitionServiceImpl implements WfProcessDefinitionService {

    private final ProcessService processService;
    private final QueryService queryService;
    private final FlwProcessMapper processMapper;
    private final FlwInstanceMapper instanceMapper;
    private final FlowLongDiagramGenerator diagramGenerator;
    private final WfProcessExtMapper processExtMapper;
    private final WfCategoryMapper categoryMapper;
    private final FlowLongIdentityService identityService;
    private final FlowLongEngine flowLongEngine;
    private final ObjectMapper objectMapper;

    @Override
    public Page<ProcessDefinitionResponse> pageDefinition(ProcessDefinitionQueryRequest request) {
        // 查询 flw_process 表获取流程定义
        LambdaQueryWrapper<FlwProcess> wrapper = new LambdaQueryWrapper<>();

        // 根据前端传入的状态参数过滤
        // 流程状态（0无效 1正常 2历史）
        if (request.getProcessState() != null) {
            wrapper.eq(FlwProcess::getProcessState, request.getProcessState());
        }else{
            wrapper.ne(FlwProcess::getProcessState, FlowState.history.getValue());
        }
        // 如果 suspensionState 为 null，不添加状态过滤条件，查询所有状态

        if (StrUtil.isNotBlank(request.getName())) {
            wrapper.like(FlwProcess::getProcessName, request.getName());
        }
        if (StrUtil.isNotBlank(request.getKey())) {
            wrapper.eq(FlwProcess::getProcessKey, request.getKey());
        }

        wrapper.orderByDesc(FlwProcess::getCreateTime);

        Page<FlwProcess> pageParam = new Page<>(request.getPageNum(), request.getPageSize());
        Page<FlwProcess> processPage = processMapper.selectPage(pageParam, wrapper);

        // 获取扩展信息和分类名称
        Set<Long> processIds = processPage.getRecords().stream()
                .map(FlwProcess::getId)
                .collect(Collectors.toSet());

        Map<Long, WfProcessExt> extMap = getProcessExtMap(processIds);
        Set<Long> categoryIds = extMap.values().stream()
                .map(WfProcessExt::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> categoryNameMap = getCategoryNameMap(new ArrayList<>(categoryIds));

        List<ProcessDefinitionResponse> records = processPage.getRecords().stream()
                .map(process -> convertToResponse(process, extMap.get(process.getId()), categoryNameMap))
                .collect(Collectors.toList());

        Page<ProcessDefinitionResponse> resultPage = new Page<>();
        resultPage.setCurrent(processPage.getCurrent());
        resultPage.setSize(processPage.getSize());
        resultPage.setTotal(processPage.getTotal());
        resultPage.setRecords(records);
        return resultPage;
    }

    @Override
    public ProcessDefinitionResponse getDefinitionById(String processDefinitionId) {
        Long id = parseProcessId(processDefinitionId);

        FlwProcess process = processService.getProcessById(id);
        if (process == null) {
            throw new BusinessException(404, "流程定义不存在");
        }

        WfProcessExt processExt = getProcessExtByProcessId(id);
        Map<Long, String> categoryNameMap = Collections.emptyMap();
        if (processExt != null && processExt.getCategoryId() != null) {
            categoryNameMap = getCategoryNameMap(List.of(processExt.getCategoryId()));
        }

        return convertToResponse(process, processExt, categoryNameMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deploy(ProcessDeployRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }

        String userName = identityService.getUserName(currentUserId);
        FlowCreator flowCreator = createFlowCreator(currentUserId);

        // 部署流程（直接使用 FlowLong JSON 格式）
        // repeat=true：每次部署都会创建新版本（版本号自动递增）
        InputStream inputStream = new ByteArrayInputStream(request.getModelJson().getBytes(StandardCharsets.UTF_8));
        Long processId = processService.deploy(inputStream, flowCreator, true, process -> {
            process.setProcessName(request.getName());
            process.setProcessType(request.getFormType() != null ? String.valueOf(request.getFormType()) : null);
            process.setRemark(request.getDescription());
        });

        // 获取部署后的流程定义
        FlwProcess process = processService.getProcessById(processId);
        if (process == null) {
            throw new BusinessException(400, "流程部署失败");
        }

        // 保存扩展信息
        WfProcessExt ext = new WfProcessExt();
        ext.setProcessId(processId);
        ext.setProcessKey(request.getKey());
        ext.setProcessName(request.getName());
        ext.setCategoryId(request.getCategoryId());
        ext.setDescription(request.getDescription());
        ext.setFormType(request.getFormType());
        ext.setFormId(request.getFormId());
        ext.setAutoCopyStrategy(request.getAutoCopyStrategy());
        ext.setAutoCopyParam(request.getAutoCopyParam());
        ext.setModelJson(request.getModelJson());
        ext.setCreateBy(currentUserId);
        ext.setCreateByName(userName);
        ext.setCreateTime(LocalDateTime.now());
        ext.setUpdateTime(LocalDateTime.now());
        ext.setDeleted(0);
        processExtMapper.insert(ext);

        log.info("流程部署成功：name={}, key={}, processId={}, deployBy={}",
                request.getName(), request.getKey(), processId, userName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void suspendDefinition(String processDefinitionId) {
        Long id = parseProcessId(processDefinitionId);

        FlwProcess process = processService.getProcessById(id);
        if (process == null) {
            throw new BusinessException(404, "流程定义不存在");
        }

        Integer currentState = process.getProcessState();
        if (currentState == null || currentState != FlowState.active.getValue()) {
            throw new BusinessException(400, "流程定义已处于挂起状态");
        }

        // 通过 Mapper 更新流程状态
        FlwProcess updateProcess = new FlwProcess();
        updateProcess.setId(id);
        updateProcess.setProcessState(FlowState.inactive.getValue());
        processMapper.updateById(updateProcess);

        log.info("流程定义已挂起：id={}, name={}", id, process.getProcessName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activateDefinition(String processDefinitionId) {
        Long id = parseProcessId(processDefinitionId);

        FlwProcess process = processService.getProcessById(id);
        if (process == null) {
            throw new BusinessException(404, "流程定义不存在");
        }

        Integer currentState = process.getProcessState();
        if (currentState != null && currentState == FlowState.active.getValue()) {
            throw new BusinessException(400, "流程定义已处于激活状态");
        }

        // 通过 Mapper 更新流程状态
        FlwProcess updateProcess = new FlwProcess();
        updateProcess.setId(id);
        updateProcess.setProcessState(FlowState.active.getValue());
        processMapper.updateById(updateProcess);

        log.info("流程定义已激活：id={}, name={}", id, process.getProcessName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDeployment(String id) {
        // FlowLong 的 deploymentId 实际上是 processId
        Long processId = parseProcessId(id);
        // 检查流程是否存在
        FlwProcess process = processService.getProcessById(processId);
        if (process == null) {
            throw new BusinessException(404, "流程定义不存在");
        }

        // 检查是否有运行中的流程实例
        LambdaQueryWrapper<FlwInstance> instanceWrapper = new LambdaQueryWrapper<>();
        instanceWrapper.eq(FlwInstance::getProcessId, processId);
        long instanceCount = instanceMapper.selectCount(instanceWrapper);
        if (instanceCount > 0) {
            throw new BusinessException(400, "存在运行中的流程实例，无法删除流程定义");
        }

        // 删除流程定义
        flowLongEngine.processService().removeById(processId);

        // 删除扩展信息
        LambdaUpdateWrapper<WfProcessExt> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(WfProcessExt::getProcessId, processId);
        wrapper.set(WfProcessExt::getProcessId, null);
        processExtMapper.update(wrapper);

        log.info("流程定义已删除：processId={}", processId);
    }

    @Override
    public String getModelJson(String processDefinitionId) {
        Long id = parseProcessId(processDefinitionId);

        FlwProcess process = processService.getProcessById(id);
        if (process == null) {
            throw new BusinessException(404, "流程定义不存在");
        }

        // 优先从扩展表获取模型 JSON
        WfProcessExt ext = getProcessExtByProcessId(id);
        if (ext != null && StrUtil.isNotBlank(ext.getModelJson())) {
            return ext.getModelJson();
        }

        // 返回 FlowLong 存储的模型内容
        if (StrUtil.isNotBlank(process.getModelContent())) {
            return process.getModelContent();
        }

        throw new BusinessException(404, "流程定义内容不存在");
    }

    @Override
    public InputStream getDiagram(String processDefinitionId) {
        Long id = parseProcessId(processDefinitionId);

        FlwProcess process = processService.getProcessById(id);
        if (process == null) {
            throw new BusinessException(404, "流程定义不存在");
        }

        // 优先使用扩展表中的 modelJson
        WfProcessExt ext = getProcessExtByProcessId(id);
        String modelContent = null;
        if (ext != null && StrUtil.isNotBlank(ext.getModelJson())) {
            modelContent = ext.getModelJson();
        } else if (StrUtil.isNotBlank(process.getModelContent())) {
            modelContent = process.getModelContent();
        }

        if (modelContent == null) {
            throw new BusinessException(404, "流程模型不存在");
        }

        // 使用 FlowLong 流程图生成器生成 SVG
        return diagramGenerator.generateDiagram(modelContent, Collections.emptySet());
    }

    @Override
    public List<UserTaskNodeResponse> getStartUserSelectTasks(String processDefinitionId) {
        Long id = parseProcessId(processDefinitionId);

        FlwProcess process = processService.getProcessById(id);
        if (process == null) {
            return Collections.emptyList();
        }

        // 优先使用扩展表中的 modelJson
        WfProcessExt ext = getProcessExtByProcessId(id);
        String modelContent = null;
        if (ext != null && StrUtil.isNotBlank(ext.getModelJson())) {
            modelContent = ext.getModelJson();
        } else if (StrUtil.isNotBlank(process.getModelContent())) {
            modelContent = process.getModelContent();
        }

        if (modelContent == null) {
            return Collections.emptyList();
        }

        // 解析 FlowLong JSON 流程模型，获取发起人自选的用户任务节点
        List<UserTaskNodeResponse> result = new ArrayList<>();
        try {
            Map<String, Object> model = objectMapper.readValue(modelContent, Map.class);
            Object nodeConfig = model.get("nodeConfig");
            if (nodeConfig instanceof Map) {
                traverseNodes((Map<String, Object>) nodeConfig, result);
            }
        } catch (Exception e) {
            log.error("解析流程模型失败：processId={}", id, e);
        }

        return result;
    }

    /**
     * 递归遍历节点树，找出发起人自选的审批节点
     */
    private void traverseNodes(Map<String, Object> node, List<UserTaskNodeResponse> result) {
        if (node == null) {
            return;
        }

        Integer type = (Integer) node.get("type");
        Integer setType = (Integer) node.get("setType");
        String nodeKey = (String) node.get("nodeKey");
        String nodeName = (String) node.get("nodeName");

        // 审批节点且发起人自选
        if (TaskType.approval.eq(type) && NodeSetType.initiatorSelected.eq(setType)) {
            UserTaskNodeResponse response = new UserTaskNodeResponse();
            response.setTaskDefKey(nodeKey);
            response.setTaskName(nodeName);
            response.setCandidateStrategy(setType);
            result.add(response);
        }

        // 遍历子节点
        Object childNode = node.get("childNode");
        if (childNode instanceof Map) {
            traverseNodes((Map<String, Object>) childNode, result);
        }

        // 遍历条件分支节点
        Object conditionNodes = node.get("conditionNodes");
        if (conditionNodes instanceof List) {
            for (Object conditionNode : (List<?>) conditionNodes) {
                if (conditionNode instanceof Map) {
                    Map<String, Object> cn = (Map<String, Object>) conditionNode;
                    Object cnChildNode = cn.get("childNode");
                    if (cnChildNode instanceof Map) {
                        traverseNodes((Map<String, Object>) cnChildNode, result);
                    }
                }
            }
        }

        // 遍历并行分支节点
        Object parallelNodes = node.get("parallelNodes");
        if (parallelNodes instanceof List) {
            for (Object parallelNode : (List<?>) parallelNodes) {
                if (parallelNode instanceof Map) {
                    Map<String, Object> pn = (Map<String, Object>) parallelNode;
                    Object pnChildNode = pn.get("childNode");
                    if (pnChildNode instanceof Map) {
                        traverseNodes((Map<String, Object>) pnChildNode, result);
                    }
                }
            }
        }

        // 遍历包容分支节点
        Object inclusiveNodes = node.get("inclusiveNodes");
        if (inclusiveNodes instanceof List) {
            for (Object inclusiveNode : (List<?>) inclusiveNodes) {
                if (inclusiveNode instanceof Map) {
                    Map<String, Object> in = (Map<String, Object>) inclusiveNode;
                    Object inChildNode = in.get("childNode");
                    if (inChildNode instanceof Map) {
                        traverseNodes((Map<String, Object>) inChildNode, result);
                    }
                }
            }
        }

        // 遍历路由分支节点
        Object routeNodes = node.get("routeNodes");
        if (routeNodes instanceof List) {
            for (Object routeNode : (List<?>) routeNodes) {
                if (routeNode instanceof Map) {
                    Map<String, Object> rn = (Map<String, Object>) routeNode;
                    Object rnChildNode = rn.get("childNode");
                    if (rnChildNode instanceof Map) {
                        traverseNodes((Map<String, Object>) rnChildNode, result);
                    }
                }
            }
        }
    }

    // ========== 私有方法 ==========

    private Long parseProcessId(String processDefinitionId) {
        try {
            return Long.parseLong(processDefinitionId);
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "流程定义ID格式错误");
        }
    }

    private FlowCreator createFlowCreator(Long userId) {
        return new FlowCreator(String.valueOf(userId), identityService.getUserName(userId));
    }

    private WfProcessExt getProcessExtByProcessId(Long processId) {
        LambdaQueryWrapper<WfProcessExt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfProcessExt::getProcessId, processId);
        return processExtMapper.selectOne(wrapper);
    }

    private Map<Long, WfProcessExt> getProcessExtMap(Set<Long> processIds) {
        if (processIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<WfProcessExt> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(WfProcessExt::getProcessId, processIds);
        List<WfProcessExt> extList = processExtMapper.selectList(wrapper);
        return extList.stream()
                .collect(Collectors.toMap(WfProcessExt::getProcessId, ext -> ext, (a, b) -> a));
    }

    private Map<Long, String> getCategoryNameMap(List<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<WfCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(WfCategory::getId, categoryIds)
                .select(WfCategory::getId, WfCategory::getCategoryName);
        List<WfCategory> categories = categoryMapper.selectList(wrapper);
        return categories.stream()
                .collect(Collectors.toMap(WfCategory::getId, WfCategory::getCategoryName, (a, b) -> a));
    }

    /**
     * 转换为响应对象
     */
    private ProcessDefinitionResponse convertToResponse(FlwProcess process,
                                                         WfProcessExt ext,
                                                         Map<Long, String> categoryNameMap) {
        ProcessDefinitionResponse response = new ProcessDefinitionResponse();
        response.setId(String.valueOf(process.getId()));
        response.setKey(process.getProcessKey());
        response.setName(process.getProcessName());
        response.setVersion(process.getProcessVersion() != null ? process.getProcessVersion() : 1);
        response.setProcessState(process.getProcessState());

        // 从扩展表获取额外信息
        if (ext != null) {
            response.setCategoryId(ext.getCategoryId());
            response.setDescription(ext.getDescription());
            response.setDeployUserName(ext.getCreateByName());
            response.setModelJson(ext.getModelJson());
            response.setFormType(ext.getFormType());
            response.setFormId(ext.getFormId());
            response.setAutoCopyStrategy(ext.getAutoCopyStrategy());
            response.setAutoCopyParam(ext.getAutoCopyParam());
            if (ext.getCreateTime() != null) {
                response.setCreateTime(ext.getCreateTime());
            }
            // 设置分类名称
            if (ext.getCategoryId() != null && categoryNameMap.containsKey(ext.getCategoryId())) {
                response.setCategoryName(categoryNameMap.get(ext.getCategoryId()));
            }
        }

        return response;
    }
}