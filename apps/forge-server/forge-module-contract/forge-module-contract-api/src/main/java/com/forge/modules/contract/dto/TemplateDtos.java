package com.forge.modules.contract.dto;

import com.forge.modules.contract.entity.CtTemplate;
import com.forge.modules.contract.entity.CtTemplateVersion;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 合同模板 DTO 集（Request/Response 内聚一处，字段量小不分文件）
 *
 * @author forge-admin
 */
public class TemplateDtos {

    private TemplateDtos() {
    }

    /** 新建模板（基础信息，状态=草稿，内容随首版保存产生） */
    @Data
    public static class CreateRequest {
        @Schema(description = "合同分类ID（须为启用叶子节点）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "合同分类不能为空")
        private Long categoryId;

        @Schema(description = "模板名称", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "模板名称不能为空")
        @Size(max = 100, message = "模板名称最长 100 字")
        private String name;

        @Schema(description = "纸张 A4/A5/LETTER/LEGAL/CUSTOM")
        private String paperSize;

        @Schema(description = "自定义纸宽 mm（CUSTOM 时必填）")
        private java.math.BigDecimal customWidthMm;

        @Schema(description = "自定义纸高 mm（CUSTOM 时必填）")
        private java.math.BigDecimal customHeightMm;

        @Size(max = 500, message = "备注最长 500 字")
        private String remark;
    }

    /** 修改模板基础信息 */
    @Data
    public static class UpdateRequest {
        @NotNull(message = "模板ID不能为空")
        private Long id;

        @NotNull(message = "合同分类不能为空")
        private Long categoryId;

        @NotBlank(message = "模板名称不能为空")
        @Size(max = 100, message = "模板名称最长 100 字")
        private String name;

        private String paperSize;
        private java.math.BigDecimal customWidthMm;
        private java.math.BigDecimal customHeightMm;

        @Size(max = 500, message = "备注最长 500 字")
        private String remark;
    }

    /** 启用/停用 */
    @Data
    public static class StatusRequest {
        @NotNull(message = "模板ID不能为空")
        private Long id;

        /** 目标状态，仅允许 1 启用 / 2 停用 */
        @NotNull(message = "目标状态不能为空")
        private Integer status;
    }

    /** 保存新版本（content + 字段定义 + 变更说明，版本号服务端自增）。
     *  templateId 由 Controller 从路径参数注入，body 不传，故不参与校验 */
    @Data
    public static class VersionSaveRequest {
        private Long templateId;

        @Schema(description = "模板内容 HTML（编辑器序列化）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "模板内容不能为空")
        private String contentHtml;

        @Schema(description = "变量定义（与内容芯片一致的完整性校验在服务端）")
        private List<FieldDef> fieldDefs;

        @Size(max = 500, message = "变更说明最长 500 字")
        private String changeLog;
    }

    /** 修正当前版本字段定义（仅属性：label/type/required/options；被合同引用的版本禁改）。
     *  versionId 由 Controller 从路径参数注入，body 不传，故不参与校验 */
    @Data
    public static class FieldDefsUpdateRequest {
        private Long versionId;

        @NotNull(message = "字段定义不能为空")
        private List<FieldDef> fieldDefs;
    }

    /** 模板列表/详情响应 */
    @Data
    public static class TemplateResponse {
        private Long id;
        private Long categoryId;
        /** 装配：分类名称/编码（ct_category） */
        private String categoryName;
        private String categoryCode;
        private String name;
        private Long currentVersionId;
        private Integer currentVersionNo;
        private Integer status;
        private String paperSize;
        private java.math.BigDecimal customWidthMm;
        private java.math.BigDecimal customHeightMm;
        private String remark;
        private Long createBy;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;

        public static TemplateResponse from(CtTemplate t) {
            TemplateResponse r = new TemplateResponse();
            r.setId(t.getId());
            r.setCategoryId(t.getCategoryId());
            r.setName(t.getName());
            r.setCurrentVersionId(t.getCurrentVersionId());
            r.setStatus(t.getStatus());
            r.setPaperSize(t.getPaperSize());
            r.setCustomWidthMm(t.getCustomWidthMm());
            r.setCustomHeightMm(t.getCustomHeightMm());
            r.setRemark(t.getRemark());
            r.setCreateBy(t.getCreateBy());
            r.setCreateTime(t.getCreateTime());
            r.setUpdateTime(t.getUpdateTime());
            return r;
        }
    }

    /** 版本响应（列表不含 content，详情含） */
    @Data
    public static class VersionResponse {
        private Long id;
        private Long templateId;
        private Integer versionNo;
        private String contentHtml;
        private List<FieldDef> fieldDefs;
        private String changeLog;
        private Long createBy;
        private LocalDateTime createTime;

        public static VersionResponse from(CtTemplateVersion v, boolean withContent) {
            VersionResponse r = new VersionResponse();
            r.setId(v.getId());
            r.setTemplateId(v.getTemplateId());
            r.setVersionNo(v.getVersionNo());
            if (withContent) {
                r.setContentHtml(v.getContentHtml());
            }
            r.setChangeLog(v.getChangeLog());
            r.setCreateBy(v.getCreateBy());
            r.setCreateTime(v.getCreateTime());
            return r;
        }
    }

    /** 启用模板下拉（合同新建用，仅 id/名称/分类/当前版本/纸张） */
    @Data
    public static class BriefResponse {
        private Long id;
        private Long categoryId;
        /** 装配：分类名称/编码（ct_category） */
        private String categoryName;
        private String categoryCode;
        private String name;
        private Long currentVersionId;
        private String paperSize;
        private java.math.BigDecimal customWidthMm;
        private java.math.BigDecimal customHeightMm;

        public static BriefResponse from(CtTemplate t) {
            BriefResponse r = new BriefResponse();
            r.setId(t.getId());
            r.setCategoryId(t.getCategoryId());
            r.setName(t.getName());
            r.setCurrentVersionId(t.getCurrentVersionId());
            r.setPaperSize(t.getPaperSize());
            r.setCustomWidthMm(t.getCustomWidthMm());
            r.setCustomHeightMm(t.getCustomHeightMm());
            return r;
        }
    }
}
