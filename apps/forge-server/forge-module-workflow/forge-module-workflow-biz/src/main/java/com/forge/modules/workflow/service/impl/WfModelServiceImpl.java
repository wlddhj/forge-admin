package com.forge.modules.workflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.aizuda.bpm.engine.ProcessService;
import com.aizuda.bpm.engine.QueryService;
import com.aizuda.bpm.engine.core.FlowCreator;
import com.aizuda.bpm.engine.entity.FlwProcess;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.forge.common.exception.BusinessException;
import com.forge.common.utils.SecurityUtils;
import com.forge.modules.workflow.dto.model.ModelQueryRequest;
import com.forge.modules.workflow.dto.model.ModelRequest;
import com.forge.modules.workflow.dto.model.ModelResponse;
import com.forge.modules.workflow.entity.WfCategory;
import com.forge.modules.workflow.entity.WfProcessExt;
import com.forge.modules.workflow.identity.FlowLongIdentityService;
import com.forge.modules.workflow.mapper.WfCategoryMapper;
import com.forge.modules.workflow.mapper.WfProcessExtMapper;
import com.forge.modules.workflow.service.WfModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 流程模型管理服务实现 - FlowLong 版本
 *
 * FlowLong 没有独立的模型表，使用 wf_process_ext 表存储模型草稿：
 * - 未部署的模型：process_id = null，表示草稿状态
 * - 已部署的模型：process_id 有值，关联 flw_process 表
 *
 * @author forge-admin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WfModelServiceImpl implements WfModelService {

    private final ProcessService processService;
    private final QueryService queryService;
    private final WfProcessExtMapper processExtMapper;
    private final WfCategoryMapper categoryMapper;
    private final FlowLongIdentityService identityService;
    private final ObjectMapper objectMapper;

    @Override
    public Page<ModelResponse> pageModel(ModelQueryRequest request) {
        // 查询 wf_process_ext 表，筛选 process_id = null 的草稿模型
        LambdaQueryWrapper<WfProcessExt> wrapper = new LambdaQueryWrapper<>();

        if (StrUtil.isNotBlank(request.getName())) {
            wrapper.like(WfProcessExt::getProcessName, request.getName());
        }
        if (StrUtil.isNotBlank(request.getKey())) {
            wrapper.eq(WfProcessExt::getProcessKey, request.getKey());
        }
        if (request.getCategoryId() != null) {
            wrapper.eq(WfProcessExt::getCategoryId, request.getCategoryId());
        }

        wrapper.orderByDesc(WfProcessExt::getUpdateTime);

        Page<WfProcessExt> pageParam = new Page<>(request.getPageNum(), request.getPageSize());
        Page<WfProcessExt> extPage = processExtMapper.selectPage(pageParam, wrapper);

        List<ModelResponse> records = extPage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        Page<ModelResponse> resultPage = new Page<>();
        resultPage.setCurrent(extPage.getCurrent());
        resultPage.setSize(extPage.getSize());
        resultPage.setTotal(extPage.getTotal());
        resultPage.setRecords(records);

        return resultPage;
    }

    @Override
    public ModelResponse getModelById(String id) {
        Long extId = parseId(id);

        WfProcessExt ext = processExtMapper.selectById(extId);
        if (ext == null) {
            throw new BusinessException(404, "模型不存在");
        }

        ModelResponse response = convertToResponse(ext);

        // 加载流程模型内容
        if (StrUtil.isNotBlank(ext.getModelJson())) {
            response.setModelJson(ext.getModelJson());
        }

        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createModel(ModelRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }

        String userName = identityService.getUserName(currentUserId);

        // 检查 key 是否重复
        LambdaQueryWrapper<WfProcessExt> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(WfProcessExt::getProcessKey, request.getKey())
                .isNull(WfProcessExt::getProcessId)
                .eq(WfProcessExt::getDeleted, 0);
        if (processExtMapper.selectCount(checkWrapper) > 0) {
            throw new BusinessException(400, "流程标识已存在");
        }

        // 构建 metaInfo
        ObjectNode metaInfoNode = objectMapper.createObjectNode();
        metaInfoNode.put("name", request.getName());
        if (StrUtil.isNotBlank(request.getDescription())) {
            metaInfoNode.put("description", request.getDescription());
        }
        if (request.getFormType() != null) {
            metaInfoNode.put("formType", request.getFormType());
        }
        if (request.getFormId() != null) {
            metaInfoNode.put("formId", request.getFormId());
        }
        if (request.getAutoCopyStrategy() != null) {
            metaInfoNode.put("autoCopyStrategy", request.getAutoCopyStrategy());
        }
        if (StrUtil.isNotBlank(request.getAutoCopyParam())) {
            metaInfoNode.put("autoCopyParam", request.getAutoCopyParam());
        }

        // 创建模型草稿（存储在 wf_process_ext 表，process_id = null）
        WfProcessExt ext = new WfProcessExt();
        ext.setProcessId(null);  // 草稿状态，未关联流程定义
        ext.setProcessKey(request.getKey());
        ext.setProcessName(request.getName());
        ext.setCategoryId(request.getCategoryId());
        ext.setDescription(request.getDescription());
        ext.setFormType(request.getFormType());
        ext.setFormId(request.getFormId());
        ext.setAutoCopyStrategy(request.getAutoCopyStrategy());
        ext.setAutoCopyParam(request.getAutoCopyParam());
        ext.setMetaInfo(metaInfoNode.toString());
        ext.setCreateBy(currentUserId);
        ext.setCreateByName(userName);
        ext.setCreateTime(LocalDateTime.now());
        ext.setUpdateTime(LocalDateTime.now());
        ext.setDeleted(0);
        processExtMapper.insert(ext);

        log.info("创建模型成功：id={}, name={}, key={}", ext.getId(), request.getName(), request.getKey());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateModel(ModelRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(400, "模型ID不能为空");
        }

        Long extId = parseId(request.getId().toString());
        WfProcessExt ext = processExtMapper.selectById(extId);
        if (ext == null) {
            throw new BusinessException(404, "模型不存在");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        String userName = identityService.getUserName(currentUserId);

        // 已部署的模型：允许更新 modelJson 和扩展信息（formId、autoCopyStrategy等），不允许修改基本信息
        if (ext.getProcessId() != null) {
            // 更新设计内容
            if (StrUtil.isNotBlank(request.getModelJson())) {
                ext.setModelJson(request.getModelJson());
            }
            // 更新扩展信息（这些不影响流程定义本身）
            if (request.getFormType() != null) {
                ext.setFormType(request.getFormType());
            }
            if (request.getFormId() != null) {
                ext.setFormId(request.getFormId());
            }
            if (request.getAutoCopyStrategy() != null) {
                ext.setAutoCopyStrategy(request.getAutoCopyStrategy());
            }
            if (request.getAutoCopyParam() != null) {
                ext.setAutoCopyParam(request.getAutoCopyParam());
            }
            ext.setUpdateTime(LocalDateTime.now());
            processExtMapper.updateById(ext);
            log.info("更新已部署模型扩展信息：id={}, name={}, formId={}", ext.getId(), ext.getProcessName(), ext.getFormId());
            return;
        }

        // 未部署的模型：允许修改所有信息
        ext.setProcessName(request.getName());
        ext.setProcessKey(request.getKey());
        ext.setCategoryId(request.getCategoryId());
        ext.setDescription(request.getDescription());
        ext.setFormType(request.getFormType());
        ext.setFormId(request.getFormId());
        ext.setAutoCopyStrategy(request.getAutoCopyStrategy());
        ext.setAutoCopyParam(request.getAutoCopyParam());
        ext.setUpdateTime(LocalDateTime.now());

        // 构建 metaInfo
        ObjectNode metaInfoNode = objectMapper.createObjectNode();
        metaInfoNode.put("name", request.getName());
        if (StrUtil.isNotBlank(request.getDescription())) {
            metaInfoNode.put("description", request.getDescription());
        }
        if (request.getFormType() != null) {
            metaInfoNode.put("formType", request.getFormType());
        }
        if (request.getFormId() != null) {
            metaInfoNode.put("formId", request.getFormId());
        }
        if (request.getAutoCopyStrategy() != null) {
            metaInfoNode.put("autoCopyStrategy", request.getAutoCopyStrategy());
        }
        if (StrUtil.isNotBlank(request.getAutoCopyParam())) {
            metaInfoNode.put("autoCopyParam", request.getAutoCopyParam());
        }
        ext.setMetaInfo(metaInfoNode.toString());

        // 更新 FlowLong JSON 模型内容
        if (StrUtil.isNotBlank(request.getModelJson())) {
            ext.setModelJson(request.getModelJson());
        }

        processExtMapper.updateById(ext);

        log.info("更新模型成功：id={}, name={}", ext.getId(), request.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deployModel(String id) {
        Long extId = parseId(id);
        WfProcessExt ext = processExtMapper.selectById(extId);
        if (ext == null) {
            throw new BusinessException(404, "模型不存在");
        }

        // 获取流程模型内容
        String modelContent = ext.getModelJson();
        if (StrUtil.isBlank(modelContent)) {
            throw new BusinessException(400, "模型内容为空，请先设计流程");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(401, "未获取到当前用户信息");
        }

        String userName = identityService.getUserName(currentUserId);
        FlowCreator flowCreator = createFlowCreator(currentUserId);

        // 部署流程（使用 FlowLong JSON 格式）
        // repeat=true：每次部署都会创建新版本（版本号自动递增）
        InputStream inputStream = new ByteArrayInputStream(modelContent.getBytes(StandardCharsets.UTF_8));
        Long processId = processService.deploy(inputStream, flowCreator, true, process -> {
            process.setProcessName(ext.getProcessName());
            process.setProcessType(ext.getFormType() != null ? String.valueOf(ext.getFormType()) : null);
            process.setRemark(ext.getDescription());
        });

        // 获取部署后的流程定义
        FlwProcess process = processService.getProcessById(processId);
        if (process == null) {
            throw new BusinessException(400, "流程部署失败");
        }

        // 更新扩展表，关联最新的流程定义ID
        ext.setProcessId(processId);
        ext.setUpdateTime(LocalDateTime.now());
        processExtMapper.updateById(ext);

        log.info("模型部署成功：modelId={}, name={}, processId={}, version={}, deployBy={}",
                extId, ext.getProcessName(), processId, process.getProcessVersion(), userName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteModel(String id) {
        Long extId = parseId(id);
        WfProcessExt ext = processExtMapper.selectById(extId);
        if (ext == null) {
            throw new BusinessException(404, "模型不存在");
        }

        // 已部署的模型不允许直接删除
        if (ext.getProcessId() != null) {
            throw new BusinessException(400, "已部署的模型不允许删除，请先删除流程定义");
        }

        processExtMapper.deleteById(extId);

        log.info("模型删除成功：id={}, name={}", extId, ext.getProcessName());
    }

    // ========== 私有方法 ==========

    private Long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "ID格式错误");
        }
    }

    private FlowCreator createFlowCreator(Long userId) {
        return new FlowCreator(String.valueOf(userId), identityService.getUserName(userId));
    }

    /**
     * 转换为响应对象
     */
    private ModelResponse convertToResponse(WfProcessExt ext) {
        ModelResponse response = new ModelResponse();
        response.setId(String.valueOf(ext.getId()));
        response.setName(ext.getProcessName());
        response.setKey(ext.getProcessKey());

        // 分类信息
        if (ext.getCategoryId() != null) {
            response.setCategoryId(ext.getCategoryId());
            WfCategory category = categoryMapper.selectById(ext.getCategoryId());
            if (category != null) {
                response.setCategoryName(category.getCategoryName());
                response.setCategory(category.getCategoryCode());
            }
        }

        response.setDescription(ext.getDescription());
        response.setFormType(ext.getFormType());
        response.setFormId(ext.getFormId());
        response.setAutoCopyStrategy(ext.getAutoCopyStrategy());
        response.setAutoCopyParam(ext.getAutoCopyParam());
        response.setCreateTime(ext.getCreateTime());
        response.setLastUpdateTime(ext.getUpdateTime());

        // 设置版本：已部署从 FlwProcess 获取，未部署默认为 "1"
        if (ext.getProcessId() != null) {
            FlwProcess process = processService.getProcessById(ext.getProcessId());
            if (process != null) {
                response.setVersion(String.valueOf(process.getProcessVersion()));
            }
        } else {
            response.setVersion("1"); // 草稿默认版本为 1
        }

        // 解析 metaInfo
        if (StrUtil.isNotBlank(ext.getMetaInfo())) {
            try {
                JsonNode metaNode = objectMapper.readTree(ext.getMetaInfo());
                if (metaNode.has("description")) {
                    response.setDescription(metaNode.get("description").asText());
                }
            } catch (Exception e) {
                log.warn("解析模型metaInfo失败：{}", ext.getMetaInfo(), e);
            }
        }

        // 检查是否已部署
        response.setDeployed(ext.getProcessId() != null);

        return response;
    }
}