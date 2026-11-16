package com.forge.modules.contract.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.exception.BusinessException;
import com.forge.common.utils.SecurityUtils;
import com.forge.modules.contract.dto.FieldDef;
import com.forge.modules.contract.dto.TemplateDtos;
import com.forge.modules.contract.entity.CtCategory;
import com.forge.modules.contract.entity.CtContract;
import com.forge.modules.contract.entity.CtTemplate;
import com.forge.modules.contract.entity.CtTemplateVersion;
import com.forge.modules.contract.enums.PaperSize;
import com.forge.modules.contract.framework.html.ContractHtmlProcessor;
import com.forge.modules.contract.mapper.CtCategoryMapper;
import com.forge.modules.contract.mapper.CtContractMapper;
import com.forge.modules.contract.mapper.CtTemplateMapper;
import com.forge.modules.contract.mapper.CtTemplateVersionMapper;
import com.forge.modules.contract.service.ContractCategoryService;
import com.forge.modules.contract.service.ContractTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 合同模板服务实现
 *
 * @author forge-admin
 */
@Service
@RequiredArgsConstructor
public class ContractTemplateServiceImpl implements ContractTemplateService {

    private final CtTemplateMapper templateMapper;
    private final CtTemplateVersionMapper versionMapper;
    private final CtContractMapper contractMapper;
    private final CtCategoryMapper categoryMapper;
    private final ContractCategoryService categoryService;
    private final ObjectMapper objectMapper;

    @Override
    public Page<TemplateDtos.TemplateResponse> pageTemplates(int pageNum, int pageSize,
                                                             String name, Long categoryId, Integer status) {
        // 选父分类含全部子孙
        List<Long> categoryIds = null;
        if (categoryId != null) {
            categoryIds = List.copyOf(categoryService.collectDescendantIds(categoryId));
            if (categoryIds.isEmpty()) {
                Page<TemplateDtos.TemplateResponse> empty = new Page<>(pageNum, pageSize, 0);
                empty.setRecords(List.of());
                return empty;
            }
        }
        Page<CtTemplate> page = templateMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<CtTemplate>()
                        .like(notBlank(name), CtTemplate::getName, name)
                        .in(categoryIds != null, CtTemplate::getCategoryId, categoryIds)
                        .eq(status != null, CtTemplate::getStatus, status)
                        .orderByDesc(CtTemplate::getUpdateTime));
        Page<TemplateDtos.TemplateResponse> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(TemplateDtos.TemplateResponse::from).toList());
        fillCurrentVersionNos(result.getRecords());
        fillCategoryInfo(result.getRecords());
        return result;
    }

    @Override
    public List<TemplateDtos.BriefResponse> listEnabled() {
        List<TemplateDtos.BriefResponse> list = templateMapper.selectList(new LambdaQueryWrapper<CtTemplate>()
                        .eq(CtTemplate::getStatus, CtTemplate.STATUS_ENABLED)
                        .isNotNull(CtTemplate::getCurrentVersionId)
                        .orderByAsc(CtTemplate::getName))
                .stream().map(TemplateDtos.BriefResponse::from).toList();
        fillBriefCategory(list);
        return list;
    }

    @Override
    public Long createTemplate(TemplateDtos.CreateRequest request) {
        validatePaper(request.getPaperSize(), request.getCustomWidthMm(), request.getCustomHeightMm());
        CtCategory category = requireLeafCategory(request.getCategoryId());
        CtTemplate t = new CtTemplate();
        t.setCategoryId(category.getId());
        t.setName(request.getName().trim());
        t.setStatus(CtTemplate.STATUS_DRAFT);
        t.setPaperSize(paperOrDefault(request.getPaperSize()));
        t.setCustomWidthMm(request.getCustomWidthMm());
        t.setCustomHeightMm(request.getCustomHeightMm());
        t.setRemark(request.getRemark());
        t.setCreateBy(SecurityUtils.getCurrentUserId());
        insertGuarded(t);
        return t.getId();
    }

    @Override
    public void updateTemplate(TemplateDtos.UpdateRequest request) {
        CtTemplate exists = requireTemplate(request.getId());
        validatePaper(request.getPaperSize(), request.getCustomWidthMm(), request.getCustomHeightMm());
        CtCategory category = requireLeafCategory(request.getCategoryId());
        CtTemplate t = new CtTemplate();
        t.setId(exists.getId());
        t.setCategoryId(category.getId());
        t.setName(request.getName().trim());
        t.setPaperSize(paperOrDefault(request.getPaperSize()));
        t.setCustomWidthMm(request.getCustomWidthMm());
        t.setCustomHeightMm(request.getCustomHeightMm());
        t.setRemark(request.getRemark());
        t.setUpdateBy(SecurityUtils.getCurrentUserId());
        try {
            templateMapper.updateById(t);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(400, "模板名称已存在");
        }
    }

    @Override
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != CtTemplate.STATUS_ENABLED && status != CtTemplate.STATUS_DISABLED)) {
            throw new BusinessException(400, "目标状态仅允许启用/停用");
        }
        CtTemplate t = requireTemplate(id);
        if (status == CtTemplate.STATUS_ENABLED && t.getCurrentVersionId() == null) {
            throw new BusinessException(400, "模板尚未保存内容版本，无法启用");
        }
        CtTemplate upd = new CtTemplate();
        upd.setId(id);
        upd.setStatus(status);
        upd.setUpdateBy(SecurityUtils.getCurrentUserId());
        templateMapper.updateById(upd);
    }

    @Override
    public TemplateDtos.TemplateResponse getTemplate(Long id) {
        TemplateDtos.TemplateResponse resp = TemplateDtos.TemplateResponse.from(requireTemplate(id));
        fillCategoryInfo(List.of(resp));
        if (resp.getCurrentVersionId() != null) {
            CtTemplateVersion v = versionMapper.selectById(resp.getCurrentVersionId());
            if (v != null) {
                resp.setCurrentVersionNo(v.getVersionNo());
            }
        }
        return resp;
    }

    @Override
    public Page<TemplateDtos.VersionResponse> pageVersions(Long templateId, int pageNum, int pageSize) {
        requireTemplate(templateId);
        Page<CtTemplateVersion> page = versionMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<CtTemplateVersion>()
                        .eq(CtTemplateVersion::getTemplateId, templateId)
                        .orderByDesc(CtTemplateVersion::getVersionNo));
        Page<TemplateDtos.VersionResponse> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream()
                .map(v -> TemplateDtos.VersionResponse.from(v, false)).toList());
        return result;
    }

    @Override
    public TemplateDtos.VersionResponse getVersion(Long versionId) {
        CtTemplateVersion v = requireVersion(versionId);
        TemplateDtos.VersionResponse resp = TemplateDtos.VersionResponse.from(v, true);
        resp.setFieldDefs(parseFieldDefs(v.getFieldDefsJson()));
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveVersion(TemplateDtos.VersionSaveRequest request) {
        if (request.getTemplateId() == null) {
            throw new BusinessException(400, "模板ID不能为空");
        }
        CtTemplate template = requireTemplate(request.getTemplateId());
        String contentHtml = ContractHtmlProcessor.sanitize(request.getContentHtml());
        if (contentHtml.isBlank()) {
            throw new BusinessException(400, "模板内容不能为空");
        }
        // 完整性校验：内容芯片 key 集与字段定义 key 集必须一致
        Set<String> contentKeys = ContractHtmlProcessor.extractFieldKeys(contentHtml);
        Set<String> defKeys = request.getFieldDefs() == null ? Set.of()
                : request.getFieldDefs().stream().map(FieldDef::getKey)
                        .filter(k -> k != null && !k.isBlank()).collect(Collectors.toSet());
        if (!contentKeys.equals(defKeys)) {
            throw new BusinessException(400, "变量定义与模板内容不一致，请保存前在编辑器内同步变量面板"
                    + diffHint(contentKeys, defKeys));
        }

        Integer maxNo = versionMapper.selectList(new LambdaQueryWrapper<CtTemplateVersion>()
                        .eq(CtTemplateVersion::getTemplateId, template.getId())
                        .orderByDesc(CtTemplateVersion::getVersionNo)
                        .last("LIMIT 1"))
                .stream().findFirst().map(CtTemplateVersion::getVersionNo).orElse(0);

        CtTemplateVersion v = new CtTemplateVersion();
        v.setTemplateId(template.getId());
        v.setVersionNo(maxNo + 1);
        v.setContentHtml(contentHtml);
        v.setFieldDefsJson(writeFieldDefs(request.getFieldDefs()));
        v.setChangeLog(request.getChangeLog());
        v.setCreateBy(SecurityUtils.getCurrentUserId());
        try {
            versionMapper.insert(v);
        } catch (DuplicateKeyException e) {
            // 并发保存同模板撞 uk_template_ver（version_no 重复），提示重试而非 500
            throw new BusinessException(409, "版本保存冲突（可能存在并发编辑），请刷新后重试");
        }

        CtTemplate upd = new CtTemplate();
        upd.setId(template.getId());
        upd.setCurrentVersionId(v.getId());
        upd.setUpdateBy(SecurityUtils.getCurrentUserId());
        templateMapper.updateById(upd);
        return v.getId();
    }

    @Override
    public void updateFieldDefs(TemplateDtos.FieldDefsUpdateRequest request) {
        CtTemplateVersion v = requireVersion(request.getVersionId());
        guardVersionReferenced(v.getId());
        // 仅属性可改：key 集必须与内容芯片一致
        Set<String> contentKeys = ContractHtmlProcessor.extractFieldKeys(v.getContentHtml());
        Set<String> defKeys = request.getFieldDefs().stream().map(FieldDef::getKey)
                .filter(k -> k != null && !k.isBlank()).collect(Collectors.toSet());
        if (!contentKeys.equals(defKeys)) {
            throw new BusinessException(400, "字段 key 集与模板内容不一致，仅允许修改变量属性");
        }
        CtTemplateVersion upd = new CtTemplateVersion();
        upd.setId(v.getId());
        upd.setFieldDefsJson(writeFieldDefs(request.getFieldDefs()));
        upd.setUpdateBy(SecurityUtils.getCurrentUserId());
        versionMapper.updateById(upd);
    }

    @Override
    public void setCurrentVersion(Long versionId) {
        CtTemplateVersion v = requireVersion(versionId);
        CtTemplate upd = new CtTemplate();
        upd.setId(v.getTemplateId());
        upd.setCurrentVersionId(v.getId());
        upd.setUpdateBy(SecurityUtils.getCurrentUserId());
        templateMapper.updateById(upd);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long id) {
        requireTemplate(id);
        boolean referenced = contractMapper.selectCount(new LambdaQueryWrapper<CtContract>()
                .eq(CtContract::getTemplateId, id).last("LIMIT 1")) > 0;
        if (referenced) {
            throw new BusinessException(400, "已有合同引用该模板，禁止删除（可停用）");
        }
        templateMapper.deleteById(id);
        versionMapper.delete(new LambdaQueryWrapper<CtTemplateVersion>()
                .eq(CtTemplateVersion::getTemplateId, id));
    }

    // ---------- 私有工具 ----------

    /** 叶子+启用分类守卫：模板仅可挂启用叶子节点 */
    private CtCategory requireLeafCategory(Long categoryId) {
        CtCategory category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(404, "合同分类不存在");
        }
        if (CtCategory.STATUS_DISABLED == category.getStatus()) {
            throw new BusinessException(400, "合同分类「" + category.getCategoryName() + "」已停用");
        }
        Long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<CtCategory>()
                .eq(CtCategory::getParentId, categoryId).last("LIMIT 1"));
        if (childCount > 0) {
            throw new BusinessException(400, "模板仅可挂在叶子分类下，「" + category.getCategoryName() + "」含子分类");
        }
        return category;
    }

    /** 列表批量装配分类名/编码（防 N+1） */
    private void fillCategoryInfo(List<TemplateDtos.TemplateResponse> records) {
        List<Long> ids = records.stream().map(TemplateDtos.TemplateResponse::getCategoryId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, CtCategory> map = categoryMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(CtCategory::getId, c -> c));
        records.forEach(r -> {
            CtCategory c = map.get(r.getCategoryId());
            if (c != null) {
                r.setCategoryName(c.getCategoryName());
                r.setCategoryCode(c.getCategoryCode());
            }
        });
    }

    private void fillBriefCategory(List<TemplateDtos.BriefResponse> records) {
        List<Long> ids = records.stream().map(TemplateDtos.BriefResponse::getCategoryId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, CtCategory> map = categoryMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(CtCategory::getId, c -> c));
        records.forEach(r -> {
            CtCategory c = map.get(r.getCategoryId());
            if (c != null) {
                r.setCategoryName(c.getCategoryName());
                r.setCategoryCode(c.getCategoryCode());
            }
        });
    }

    private void fillCurrentVersionNos(List<TemplateDtos.TemplateResponse> records) {
        List<Long> versionIds = records.stream().map(TemplateDtos.TemplateResponse::getCurrentVersionId)
                .filter(Objects::nonNull).toList();
        if (versionIds.isEmpty()) {
            return;
        }
        Map<Long, Integer> noMap = new HashMap<>();
        for (CtTemplateVersion v : versionMapper.selectBatchIds(versionIds)) {
            noMap.put(v.getId(), v.getVersionNo());
        }
        records.forEach(r -> r.setCurrentVersionNo(noMap.get(r.getCurrentVersionId())));
    }

    private CtTemplate requireTemplate(Long id) {
        CtTemplate t = templateMapper.selectById(id);
        if (t == null) {
            throw new BusinessException(404, "模板不存在");
        }
        return t;
    }

    private CtTemplateVersion requireVersion(Long versionId) {
        CtTemplateVersion v = versionMapper.selectById(versionId);
        if (v == null) {
            throw new BusinessException(404, "模板版本不存在");
        }
        return v;
    }

    private void guardVersionReferenced(Long versionId) {
        boolean referenced = contractMapper.selectCount(new LambdaQueryWrapper<CtContract>()
                .eq(CtContract::getTemplateVersionId, versionId).last("LIMIT 1")) > 0;
        if (referenced) {
            throw new BusinessException(400, "该版本已被合同引用，禁止修改字段定义");
        }
    }

    private void insertGuarded(CtTemplate t) {
        try {
            templateMapper.insert(t);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(400, "模板名称已存在");
        }
    }

    private void validatePaper(String paperSize, java.math.BigDecimal w, java.math.BigDecimal h) {
        if (paperSize != null && PaperSize.CUSTOM.name().equalsIgnoreCase(paperSize)) {
            boolean invalid = w == null || h == null || w.signum() <= 0 || h.signum() <= 0;
            if (invalid) {
                throw new BusinessException(400, "自定义纸张须提供正的宽高（mm）");
            }
        }
    }

    private String paperOrDefault(String paperSize) {
        return notBlank(paperSize) ? PaperSize.of(paperSize).name() : PaperSize.A4.name();
    }

    private String writeFieldDefs(List<FieldDef> defs) {
        if (defs == null || defs.isEmpty()) {
            return "[]";
        }
        return objectMapper.writeValueAsString(defs);
    }

    private List<FieldDef> parseFieldDefs(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return objectMapper.readValue(json, new TypeReference<List<FieldDef>>() {
        });
    }

    private String diffHint(Set<String> contentKeys, Set<String> defKeys) {
        Set<String> onlyInContent = new HashSet<>(contentKeys);
        onlyInContent.removeAll(defKeys);
        Set<String> onlyInDefs = new HashSet<>(defKeys);
        onlyInDefs.removeAll(contentKeys);
        StringBuilder sb = new StringBuilder("。");
        if (!onlyInContent.isEmpty()) {
            sb.append("内容中未定义：").append(String.join("、", onlyInContent)).append("；");
        }
        if (!onlyInDefs.isEmpty()) {
            sb.append("定义中多余：").append(String.join("、", onlyInDefs));
        }
        return sb.toString();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
