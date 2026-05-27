package com.forge.admin.modules.workflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.forge.admin.common.exception.BusinessException;
import com.forge.admin.common.utils.SecurityUtils;
import com.forge.admin.modules.workflow.dto.model.ModelQueryRequest;
import com.forge.admin.modules.workflow.dto.model.ModelRequest;
import com.forge.admin.modules.workflow.dto.model.ModelResponse;
import com.forge.admin.modules.workflow.entity.WfCategory;
import com.forge.admin.modules.workflow.entity.WfProcessDeployExt;
import com.forge.admin.modules.workflow.identity.FlowableIdentityService;
import com.forge.admin.modules.workflow.mapper.WfCategoryMapper;
import com.forge.admin.modules.workflow.mapper.WfProcessDeployExtMapper;
import com.forge.admin.modules.workflow.service.WfModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.Model;
import org.flowable.engine.repository.ModelQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程模型管理服务实现
 *
 * @author forge-admin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfModelServiceImpl implements WfModelService {

    private final RepositoryService repositoryService;
    private final WfProcessDeployExtMapper wfProcessDeployExtMapper;
    private final WfCategoryMapper wfCategoryMapper;
    private final FlowableIdentityService flowableIdentityService;
    private final ObjectMapper objectMapper;

    @Override
    public Page<ModelResponse> pageModel(ModelQueryRequest request) {
        ModelQuery query = repositoryService.createModelQuery();

        if (StrUtil.isNotBlank(request.getName())) {
            query.modelNameLike("%" + request.getName() + "%");
        }
        if (StrUtil.isNotBlank(request.getKey())) {
            query.modelKey(request.getKey());
        }
        if (StrUtil.isNotBlank(request.getCategory())) {
            query.modelCategory(request.getCategory());
        }
        query.orderByLastUpdateTime().desc();

        long total = query.count();
        int offset = (request.getPageNum() - 1) * request.getPageSize();
        List<Model> models = query.listPage(offset, request.getPageSize());

        List<ModelResponse> records = models.stream()
                .map(this::convertToResponse)
                .toList();

        Page<ModelResponse> resultPage = new Page<>();
        resultPage.setCurrent(request.getPageNum());
        resultPage.setSize(request.getPageSize());
        resultPage.setTotal(total);
        resultPage.setRecords(records);

        return resultPage;
    }

    @Override
    public ModelResponse getModelById(String id) {
        Model model = repositoryService.getModel(id);
        if (model == null) {
            throw new BusinessException(404, "模型不存在");
        }
        ModelResponse response = convertToResponse(model);

        // 加载编辑器源内容（BPMN XML）
        byte[] editorSource = repositoryService.getModelEditorSource(id);
        if (editorSource != null) {
            response.setBpmnXml(new String(editorSource, StandardCharsets.UTF_8));
        }

        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createModel(ModelRequest request) {
        // 构建元信息 JSON
        ObjectNode metaInfoNode = objectMapper.createObjectNode();
        metaInfoNode.put("name", request.getName());
        if (StrUtil.isNotBlank(request.getDescription())) {
            metaInfoNode.put("description", request.getDescription());
        }
        // 合并前端传入的 metaInfo（包含 formType、formId）
        mergeMetaInfo(metaInfoNode, request.getMetaInfo());

        Model model = repositoryService.newModel();
        model.setName(request.getName());
        model.setKey(request.getKey());
        model.setCategory(request.getCategory());
        model.setVersion(1);
        model.setMetaInfo(metaInfoNode.toString());

        repositoryService.saveModel(model);

        // 创建初始 BPMN XML（空白流程：开始事件 -> 结束事件）
        String initialBpmnXml = buildInitialBpmnXml(request.getKey(), request.getName());
        repositoryService.addModelEditorSource(model.getId(),
                initialBpmnXml.getBytes(StandardCharsets.UTF_8));

        log.info("创建模型成功：id={}, name={}, key={}", model.getId(), request.getName(), request.getKey());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateModel(ModelRequest request) {
        if (StrUtil.isBlank(request.getId())) {
            throw new BusinessException(400, "模型ID不能为空");
        }

        Model model = repositoryService.getModel(request.getId());
        if (model == null) {
            throw new BusinessException(404, "模型不存在");
        }

        model.setName(request.getName());
        model.setKey(request.getKey());
        model.setCategory(request.getCategory());

        // 构建元信息
        ObjectNode metaInfoNode = objectMapper.createObjectNode();
        metaInfoNode.put("name", request.getName());
        if (StrUtil.isNotBlank(request.getDescription())) {
            metaInfoNode.put("description", request.getDescription());
        }
        // 合并前端传入的 metaInfo（包含 formType、formId）
        mergeMetaInfo(metaInfoNode, request.getMetaInfo());
        model.setMetaInfo(metaInfoNode.toString());

        repositoryService.saveModel(model);

        // 如果请求包含 BPMN XML，更新编辑器源
        if (StrUtil.isNotBlank(request.getBpmnXml())) {
            repositoryService.addModelEditorSource(model.getId(),
                    request.getBpmnXml().getBytes(StandardCharsets.UTF_8));
        }

        log.info("更新模型成功：id={}, name={}", model.getId(), request.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deployModel(String id) {
        Model model = repositoryService.getModel(id);
        if (model == null) {
            throw new BusinessException(404, "模型不存在");
        }

        // 获取编辑器源（BPMN XML）
        byte[] editorSource = repositoryService.getModelEditorSource(id);
        if (editorSource == null || editorSource.length == 0) {
            throw new BusinessException(400, "模型内容为空，请先设计流程");
        }

        String bpmnXml = new String(editorSource, StandardCharsets.UTF_8);
        if (StrUtil.isBlank(bpmnXml)) {
            throw new BusinessException(400, "模型内容为空，请先设计流程");
        }

        // 确保 BPMN XML 中 process 的 id 和 name 与模型一致
        String processKey = escapeXml(model.getKey());
        String processName = escapeXml(model.getName());

        // 先移除已有的 name 属性（非贪婪匹配，避免跳过 name）
        String beforeRemove = bpmnXml;
        bpmnXml = bpmnXml.replaceFirst(
                "(<bpmn:process[^>]*?)\\s+name=\"[^\"]*\"",
                "$1"
        );

        // 替换 <bpmn:process ... id="xxx" ...> 中的 id 并添加 name
        String afterStep2 = bpmnXml.replaceFirst(
                "<bpmn:process([^>]*?)\\s+id=\"[^\"]*\"",
                "<bpmn:process$1 id=\"" + processKey + "\" name=\"" + processName + "\""
        );

        // 如果 Step 2 成功替换，使用结果；否则尝试 id 在最前面的情况
        if (!afterStep2.equals(bpmnXml)) {
            bpmnXml = afterStep2;
        } else {
            bpmnXml = bpmnXml.replaceFirst(
                    "<bpmn:process\\s+id=\"[^\"]*\"",
                    "<bpmn:process id=\"" + processKey + "\" name=\"" + processName + "\""
            );
        }

        // 设置 Flowable 认证用户
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String currentUsername = SecurityUtils.getCurrentUsername();
        flowableIdentityService.setAuthenticatedUserId(currentUserId);

        try {
            // 部署
            String deploymentName = model.getName();
            Deployment deployment = repositoryService.createDeployment()
                    .name(deploymentName)
                    .addString(model.getName() + ".bpmn20.xml", bpmnXml)
                    .category(model.getCategory())
                    .key(model.getKey())
                    .name(model.getName())
                    .deploy();

            // 获取新部署的流程定义
            ProcessDefinition newDefinition = repositoryService.createProcessDefinitionQuery()
                    .deploymentId(deployment.getId())
                    .latestVersion()
                    .singleResult();

            if (newDefinition == null) {
                repositoryService.deleteDeployment(deployment.getId(), true);
                throw new BusinessException(400, "BPMN XML内容无效，未能生成流程定义");
            }

            // 从 metaInfo 解析 formType 和 formId
            Integer formType = null;
            Long formId = null;
            String metaInfo = model.getMetaInfo();
            if (StrUtil.isNotBlank(metaInfo)) {
                try {
                    JsonNode metaNode = objectMapper.readTree(metaInfo);
                    if (metaNode.has("formType")) {
                        formType = metaNode.get("formType").asInt();
                    }
                    if (metaNode.has("formId")) {
                        formId = metaNode.get("formId").asLong();
                    }
                } catch (Exception e) {
                    log.warn("解析模型metaInfo失败：{}", metaInfo, e);
                }
            }

            // 从模型分类编码查询分类ID
            Long categoryId = null;
            if (StrUtil.isNotBlank(model.getCategory())) {
                try {
                    WfCategory category = wfCategoryMapper.selectOne(
                            new LambdaQueryWrapper<WfCategory>()
                                    .eq(WfCategory::getCategoryCode, model.getCategory())
                                    .last("LIMIT 1")
                    );
                    if (category != null) {
                        categoryId = category.getId();
                    }
                } catch (Exception e) {
                    log.warn("查询分类失败：category={}", model.getCategory(), e);
                }
            }

            // 保存扩展信息
            WfProcessDeployExt ext = new WfProcessDeployExt();
            ext.setDeploymentId(deployment.getId());
            ext.setProcessDefinitionId(newDefinition.getId());
            ext.setProcessKey(newDefinition.getKey());
            ext.setProcessName(model.getName());  // 使用模型名称
            ext.setCategoryId(categoryId);  // 设置分类ID
            ext.setFormType(formType);
            ext.setFormId(formId);
            ext.setBpmnXml(bpmnXml);
            ext.setCreateBy(currentUserId);
            ext.setCreateByName(currentUsername);
            ext.setCreateTime(LocalDateTime.now());
            ext.setUpdateTime(LocalDateTime.now());
            ext.setDeleted(0);
            wfProcessDeployExtMapper.insert(ext);

            log.info("模型部署成功：modelId={}, name={}, deploymentId={}, deployBy={}",
                    id, model.getName(), deployment.getId(), currentUsername);
        } finally {
            flowableIdentityService.clearAuthenticatedUserId();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteModel(String id) {
        Model model = repositoryService.getModel(id);
        if (model == null) {
            throw new BusinessException(404, "模型不存在");
        }

        repositoryService.deleteModel(id);
        log.info("模型删除成功：id={}, name={}", id, model.getName());
    }

    /**
     * 转换为响应对象
     */
    private ModelResponse convertToResponse(Model model) {
        ModelResponse response = new ModelResponse();
        response.setId(model.getId());
        response.setName(model.getName());
        response.setKey(model.getKey());
        response.setCategory(model.getCategory());
        response.setMetaInfo(model.getMetaInfo());
        response.setCreateTime(model.getCreateTime());
        response.setLastUpdateTime(model.getLastUpdateTime());
        if (model.getVersion() != null) {
            response.setVersion(model.getVersion().toString());
        }

        // 解析 metaInfo 获取 description、formType 和 formId
        String metaInfo = model.getMetaInfo();
        if (StrUtil.isNotBlank(metaInfo)) {
            try {
                JsonNode metaNode = objectMapper.readTree(metaInfo);
                if (metaNode.has("description")) {
                    response.setDescription(metaNode.get("description").asText());
                }
                if (metaNode.has("formType")) {
                    response.setFormType(metaNode.get("formType").asInt());
                }
                if (metaNode.has("formId")) {
                    response.setFormId(metaNode.get("formId").asLong());
                }
            } catch (Exception e) {
                log.warn("解析模型metaInfo失败：{}", metaInfo, e);
            }
        }

        // 检查是否已部署：通过 key 查找是否有对应的流程定义
        if (StrUtil.isNotBlank(model.getKey())) {
            long deployedCount = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionKey(model.getKey())
                    .count();
            response.setDeployed(deployedCount > 0);
        }

        return response;
    }

    /**
     * 合并前端传入的 metaInfo JSON 到目标节点
     */
    private void mergeMetaInfo(ObjectNode target, String metaInfoJson) {
        if (StrUtil.isBlank(metaInfoJson)) {
            return;
        }
        try {
            JsonNode source = objectMapper.readTree(metaInfoJson);
            source.fields().forEachRemaining(entry -> {
                if (!target.has(entry.getKey())) {
                    target.set(entry.getKey(), entry.getValue());
                }
            });
        } catch (Exception e) {
            log.warn("解析metaInfo失败：{}", metaInfoJson, e);
        }
    }

    /**
     * 构建初始 BPMN XML（包含开始事件和结束事件）
     */
    private String buildInitialBpmnXml(String processKey, String processName) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://flowable.org/test">
                  <process id="%s" name="%s" isExecutable="true">
                    <startEvent id="startEvent" name="开始"/>
                    <endEvent id="endEvent" name="结束"/>
                    <sequenceFlow id="flow1" sourceRef="startEvent" targetRef="endEvent"/>
                  </process>
                </definitions>
                """.formatted(
                escapeXml(processKey),
                escapeXml(processName)
        );
    }

    /**
     * XML 特殊字符转义
     */
    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
