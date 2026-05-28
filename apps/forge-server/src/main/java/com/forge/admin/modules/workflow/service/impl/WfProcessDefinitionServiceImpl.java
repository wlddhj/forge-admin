package com.forge.admin.modules.workflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.common.exception.BusinessException;
import com.forge.admin.common.utils.SecurityUtils;
import com.forge.admin.modules.workflow.dto.definition.ProcessDefinitionQueryRequest;
import com.forge.admin.modules.workflow.dto.definition.ProcessDefinitionResponse;
import com.forge.admin.modules.workflow.dto.definition.ProcessDeployRequest;
import com.forge.admin.modules.workflow.dto.definition.UserTaskNodeResponse;
import com.forge.admin.modules.workflow.entity.WfCategory;
import com.forge.admin.modules.workflow.entity.WfProcessDeployExt;
import com.forge.admin.modules.workflow.mapper.WfCategoryMapper;
import com.forge.admin.modules.workflow.mapper.WfProcessDeployExtMapper;
import com.forge.admin.modules.workflow.service.WfProcessDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowableListener;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.image.ProcessDiagramGenerator;
import org.flowable.image.impl.DefaultProcessDiagramGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 流程定义管理服务实现
 *
 * @author forge-admin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfProcessDefinitionServiceImpl implements WfProcessDefinitionService {

    private final RepositoryService repositoryService;
    private final WfProcessDeployExtMapper wfProcessDeployExtMapper;
    private final WfCategoryMapper wfCategoryMapper;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Page<ProcessDefinitionResponse> pageDefinition(ProcessDefinitionQueryRequest request) {
        // 构建查询条件
        ProcessDefinitionQuery query = repositoryService.createProcessDefinitionQuery()
                .latestVersion();

        if (StrUtil.isNotBlank(request.getName())) {
            query.processDefinitionNameLike("%" + request.getName() + "%");
        }
        if (StrUtil.isNotBlank(request.getKey())) {
            query.processDefinitionKey(request.getKey());
        }
        if (request.getSuspensionState() != null) {
            if (request.getSuspensionState() == 1) {
                query.active();
            } else if (request.getSuspensionState() == 2) {
                query.suspended();
            }
        }

        // 获取所有流程定义（不使用 Flowable 的分页，因为需要按扩展信息排序）
        List<ProcessDefinition> allDefinitions = query.list();

        if (allDefinitions.isEmpty()) {
            Page<ProcessDefinitionResponse> emptyPage = new Page<>();
            emptyPage.setCurrent(request.getPageNum());
            emptyPage.setSize(request.getPageSize());
            emptyPage.setTotal(0L);
            emptyPage.setRecords(Collections.emptyList());
            return emptyPage;
        }

        // 批量获取部署扩展信息
        List<String> deploymentIds = allDefinitions.stream()
                .map(ProcessDefinition::getDeploymentId)
                .distinct()
                .toList();
        Map<String, WfProcessDeployExt> extMap = getDeployExtMap(deploymentIds);

        // 如果有分类过滤条件，先筛选
        List<ProcessDefinition> filteredDefinitions = allDefinitions;
        if (request.getCategoryId() != null) {
            filteredDefinitions = allDefinitions.stream()
                    .filter(def -> {
                        WfProcessDeployExt ext = extMap.get(def.getDeploymentId());
                        return ext != null && request.getCategoryId().equals(ext.getCategoryId());
                    })
                    .toList();
        }

        // 按创建时间降序排序
        filteredDefinitions = filteredDefinitions.stream()
                .sorted((a, b) -> {
                    WfProcessDeployExt extA = extMap.get(a.getDeploymentId());
                    WfProcessDeployExt extB = extMap.get(b.getDeploymentId());
                    if (extA == null && extB == null) return 0;
                    if (extA == null) return 1;
                    if (extB == null) return -1;
                    if (extA.getCreateTime() == null && extB.getCreateTime() == null) return 0;
                    if (extA.getCreateTime() == null) return 1;
                    if (extB.getCreateTime() == null) return -1;
                    return extB.getCreateTime().compareTo(extA.getCreateTime());
                })
                .toList();

        // 手动分页
        long total = filteredDefinitions.size();
        int offset = (request.getPageNum() - 1) * request.getPageSize();
        int endIndex = Math.min(offset + request.getPageSize(), filteredDefinitions.size());
        List<ProcessDefinition> pagedDefinitions = filteredDefinitions.subList(offset, endIndex);

        // 获取分类名称映射
        List<Long> categoryIds = extMap.values().stream()
                .map(WfProcessDeployExt::getCategoryId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, String> categoryNameMap = getCategoryNameMap(categoryIds);

        // 转换为响应对象
        List<ProcessDefinitionResponse> records = pagedDefinitions.stream()
                .map(def -> convertToResponse(def, extMap.get(def.getDeploymentId()), categoryNameMap))
                .toList();

        // 组装分页结果
        Page<ProcessDefinitionResponse> resultPage = new Page<>();
        resultPage.setCurrent(request.getPageNum());
        resultPage.setSize(request.getPageSize());
        resultPage.setTotal(total);
        resultPage.setRecords(records);

        return resultPage;
    }

    @Override
    public ProcessDefinitionResponse getDefinitionById(String processDefinitionId) {
        ProcessDefinition definition = repositoryService.getProcessDefinition(processDefinitionId);
        if (definition == null) {
            throw new BusinessException(404, "流程定义不存在");
        }

        WfProcessDeployExt ext = getDeployExtByDeploymentId(definition.getDeploymentId());
        Map<Long, String> categoryNameMap = Collections.emptyMap();
        if (ext != null && ext.getCategoryId() != null) {
            categoryNameMap = getCategoryNameMap(List.of(ext.getCategoryId()));
        }

        return convertToResponse(definition, ext, categoryNameMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deploy(ProcessDeployRequest request) {
        // 部署流程定义
        Deployment deployment = repositoryService.createDeployment()
                .addString(request.getName() + ".bpmn20.xml", request.getBpmnXml())
                .name(request.getName())
                .deploy();

        // 获取新部署的流程定义
        ProcessDefinition newDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .latestVersion()
                .singleResult();

        if (newDefinition == null) {
            // 回滚部署
            repositoryService.deleteDeployment(deployment.getId(), true);
            throw new BusinessException(400, "BPMN XML内容无效，未能生成流程定义");
        }

        // 保存扩展信息
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String currentUsername = SecurityUtils.getCurrentUsername();

        WfProcessDeployExt ext = new WfProcessDeployExt();
        ext.setDeploymentId(deployment.getId());
        ext.setProcessDefinitionId(newDefinition.getId());
        ext.setProcessKey(request.getKey());
        ext.setProcessName(request.getName());
        ext.setCategoryId(request.getCategoryId());
        ext.setDescription(request.getDescription());
        ext.setFormType(request.getFormType());
        ext.setFormId(request.getFormId());
        ext.setBpmnXml(request.getBpmnXml());
        ext.setCreateBy(currentUserId);
        ext.setCreateByName(currentUsername);
        ext.setCreateTime(LocalDateTime.now());
        ext.setUpdateTime(LocalDateTime.now());
        ext.setDeleted(0);
        wfProcessDeployExtMapper.insert(ext);

        log.info("流程部署成功：name={}, key={}, deploymentId={}, deployBy={}",
                request.getName(), request.getKey(), deployment.getId(), currentUsername);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void suspendDefinition(String processDefinitionId) {
        ProcessDefinition definition = repositoryService.getProcessDefinition(processDefinitionId);
        if (definition == null) {
            throw new BusinessException(404, "流程定义不存在");
        }
        if (definition.isSuspended()) {
            throw new BusinessException(400, "流程定义已处于挂起状态");
        }
        repositoryService.suspendProcessDefinitionById(processDefinitionId, true, null);
        log.info("流程定义已挂起：id={}, name={}", processDefinitionId, definition.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activateDefinition(String processDefinitionId) {
        ProcessDefinition definition = repositoryService.getProcessDefinition(processDefinitionId);
        if (definition == null) {
            throw new BusinessException(404, "流程定义不存在");
        }
        if (!definition.isSuspended()) {
            throw new BusinessException(400, "流程定义已处于激活状态");
        }
        repositoryService.activateProcessDefinitionById(processDefinitionId, true, null);
        log.info("流程定义已激活：id={}, name={}", processDefinitionId, definition.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDeployment(String deploymentId) {
        // 检查部署是否存在
        long count = repositoryService.createDeploymentQuery()
                .deploymentId(deploymentId)
                .count();
        if (count == 0) {
            throw new BusinessException(404, "部署不存在");
        }

        // 级联删除部署（包括流程实例）
        repositoryService.deleteDeployment(deploymentId, true);

        // 删除扩展信息
        LambdaQueryWrapper<WfProcessDeployExt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfProcessDeployExt::getDeploymentId, deploymentId);
        wfProcessDeployExtMapper.delete(wrapper);

        log.info("流程部署已删除：deploymentId={}", deploymentId);
    }

    @Override
    public String getBpmnXml(String processDefinitionId) {
        ProcessDefinition definition = repositoryService.getProcessDefinition(processDefinitionId);
        if (definition == null) {
            throw new BusinessException(404, "流程定义不存在");
        }

        // 优先从扩展表获取
        WfProcessDeployExt ext = getDeployExtByDeploymentId(definition.getDeploymentId());
        if (ext != null && StrUtil.isNotBlank(ext.getBpmnXml())) {
            return ext.getBpmnXml();
        }

        // 从部署资源获取
        List<String> resourceNames = repositoryService.getDeploymentResourceNames(definition.getDeploymentId());
        String bpmnResourceName = resourceNames.stream()
                .filter(name -> name.endsWith(".bpmn20.xml") || name.endsWith(".bpmn"))
                .findFirst()
                .orElse(null);

        if (bpmnResourceName == null) {
            throw new BusinessException(404, "BPMN XML资源不存在");
        }

        try (InputStream inputStream = repositoryService.getResourceAsStream(
                definition.getDeploymentId(), bpmnResourceName)) {
            return new String(inputStream.readAllBytes());
        } catch (Exception e) {
            log.error("获取BPMN XML失败：processDefinitionId={}", processDefinitionId, e);
            throw new BusinessException(500, "获取BPMN XML失败");
        }
    }

    @Override
    public InputStream getDiagram(String processDefinitionId) {
        ProcessDefinition definition = repositoryService.getProcessDefinition(processDefinitionId);
        if (definition == null) {
            throw new BusinessException(404, "流程定义不存在");
        }

        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
        if (bpmnModel == null || bpmnModel.getProcesses().isEmpty()) {
            throw new BusinessException(404, "流程模型不存在");
        }

        // 使用 DefaultProcessDiagramGenerator 生成流程图
        ProcessDiagramGenerator diagramGenerator = new DefaultProcessDiagramGenerator();
        return diagramGenerator.generateDiagram(
                bpmnModel,
                "png",
                Collections.emptyList(),
                Collections.emptyList(),
                "宋体",
                "宋体",
                "宋体",
                null,
                1.0,
                true
        );
    }

    /**
     * 批量获取部署扩展信息映射
     */
    private Map<String, WfProcessDeployExt> getDeployExtMap(List<String> deploymentIds) {
        if (deploymentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<WfProcessDeployExt> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(WfProcessDeployExt::getDeploymentId, deploymentIds);
        List<WfProcessDeployExt> extList = wfProcessDeployExtMapper.selectList(wrapper);
        return extList.stream()
                .collect(Collectors.toMap(WfProcessDeployExt::getDeploymentId, Function.identity(), (a, b) -> a));
    }

    /**
     * 根据部署ID获取扩展信息
     */
    private WfProcessDeployExt getDeployExtByDeploymentId(String deploymentId) {
        LambdaQueryWrapper<WfProcessDeployExt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfProcessDeployExt::getDeploymentId, deploymentId);
        return wfProcessDeployExtMapper.selectOne(wrapper);
    }

    /**
     * 获取分类名称映射
     */
    private Map<Long, String> getCategoryNameMap(List<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<WfCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(WfCategory::getId, categoryIds)
                .select(WfCategory::getId, WfCategory::getCategoryName);
        List<WfCategory> categories = wfCategoryMapper.selectList(wrapper);
        return categories.stream()
                .collect(Collectors.toMap(WfCategory::getId, WfCategory::getCategoryName, (a, b) -> a));
    }

    /**
     * 转换为响应对象
     */
    private ProcessDefinitionResponse convertToResponse(ProcessDefinition definition,
                                                        WfProcessDeployExt ext,
                                                        Map<Long, String> categoryNameMap) {
        ProcessDefinitionResponse response = new ProcessDefinitionResponse();
        response.setId(definition.getId());
        response.setKey(definition.getKey());
        response.setName(definition.getName());
        response.setVersion(definition.getVersion());
        response.setDeploymentId(definition.getDeploymentId());
        response.setSuspensionState(definition.isSuspended() ? 2 : 1);
        response.setResourceName(definition.getResourceName());
        response.setDiagramResourceName(definition.getDiagramResourceName());

        // 从扩展表获取额外信息
        if (ext != null) {
            response.setCategoryId(ext.getCategoryId());
            response.setDescription(ext.getDescription());
            response.setDeployUserName(ext.getCreateByName());
            response.setBpmnXml(ext.getBpmnXml());
            response.setFormType(ext.getFormType());
            response.setFormId(ext.getFormId());
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

    @Override
    public List<UserTaskNodeResponse> getStartUserSelectTasks(String processDefinitionId) {
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
        if (bpmnModel == null || bpmnModel.getProcesses().isEmpty()) {
            return Collections.emptyList();
        }

        List<UserTaskNodeResponse> result = new ArrayList<>();
        for (org.flowable.bpmn.model.Process process : bpmnModel.getProcesses()) {
            for (UserTask userTask : process.findFlowElementsOfType(UserTask.class)) {
                String strategyStr = userTask.getAttributeValue(
                        "http://flowable.org/bpmn", "candidateStrategy");
                if (strategyStr != null) {
                    try {
                        int strategy = Integer.parseInt(strategyStr);
                        // 34=审批人自选, 35=发起人自选
                        if (strategy == 34 || strategy == 35) {
                            UserTaskNodeResponse node = new UserTaskNodeResponse();
                            node.setTaskDefKey(userTask.getId());
                            node.setTaskName(userTask.getName());
                            node.setCandidateStrategy(strategy);
                            result.add(node);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return result;
    }
}
