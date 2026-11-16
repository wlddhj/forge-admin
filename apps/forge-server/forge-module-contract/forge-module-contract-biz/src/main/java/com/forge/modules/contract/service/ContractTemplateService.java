package com.forge.modules.contract.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.contract.dto.TemplateDtos;

/**
 * 合同模板服务
 *
 * @author forge-admin
 */
public interface ContractTemplateService {

    /** 分页查询模板 */
    Page<TemplateDtos.TemplateResponse> pageTemplates(int pageNum, int pageSize,
                                                      String name, Long categoryId, Integer status);

    /** 启用中的模板下拉（合同新建用） */
    java.util.List<TemplateDtos.BriefResponse> listEnabled();

    /** 新建模板（基础信息，状态=草稿） */
    Long createTemplate(TemplateDtos.CreateRequest request);

    /** 修改模板基础信息 */
    void updateTemplate(TemplateDtos.UpdateRequest request);

    /** 启用/停用（启用要求已有内容版本） */
    void changeStatus(Long id, Integer status);

    /** 模板详情 */
    TemplateDtos.TemplateResponse getTemplate(Long id);

    /** 分页查询版本（不含 content） */
    Page<TemplateDtos.VersionResponse> pageVersions(Long templateId, int pageNum, int pageSize);

    /** 版本详情（含 content 与字段定义） */
    TemplateDtos.VersionResponse getVersion(Long versionId);

    /** 保存新版本（sanitize + 芯片完整性校验，版本号自增，current_version_id 前移） */
    Long saveVersion(TemplateDtos.VersionSaveRequest request);

    /** 修正当前版本字段定义（仅属性；被合同引用的版本禁改） */
    void updateFieldDefs(TemplateDtos.FieldDefsUpdateRequest request);

    /** 回滚：将历史版本设为当前版本 */
    void setCurrentVersion(Long versionId);

    /** 删除模板（存在关联合同时禁删） */
    void deleteTemplate(Long id);
}
