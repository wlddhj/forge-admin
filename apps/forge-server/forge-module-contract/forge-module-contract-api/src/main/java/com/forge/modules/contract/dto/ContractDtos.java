package com.forge.modules.contract.dto;

import com.forge.modules.contract.entity.CtContract;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 合同 DTO 集（Request/Response 内聚一处，字段量小不分文件）
 *
 * @author forge-admin
 */
public class ContractDtos {

    private ContractDtos() {
    }

    /** 新建合同草稿（锁定模板当前版本） */
    @Data
    public static class CreateRequest {
        @Schema(description = "模板ID（须为启用状态）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "模板ID不能为空")
        private Long templateId;

        @Schema(description = "合同标题", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "合同标题不能为空")
        @Size(max = 200, message = "合同标题最长 200 字")
        private String title;

        @Schema(description = "合同编号（选填，租户内唯一；留空则审批通过后自动生成）")
        @Size(max = 64, message = "合同编号最长 64 字")
        private String contractNo;

        @Schema(description = "备注（管理性信息，不参与合同渲染）")
        @Size(max = 500, message = "备注最长 500 字")
        private String remark;

        @Schema(description = "变量初值（可空，草稿期可反复编辑）")
        private Map<String, Object> fieldValues;
    }

    /** 编辑草稿（仅 status=0/3/4 可改；重置回草稿语义由服务端处理） */
    @Data
    public static class UpdateRequest {
        @NotNull(message = "合同ID不能为空")
        private Long id;

        @NotBlank(message = "合同标题不能为空")
        @Size(max = 200, message = "合同标题最长 200 字")
        private String title;

        @Schema(description = "合同编号（选填，草稿期可改；留空则审批通过后自动生成）")
        @Size(max = 64, message = "合同编号最长 64 字")
        private String contractNo;

        @Schema(description = "备注（管理性信息，不参与合同渲染）")
        @Size(max = 500, message = "备注最长 500 字")
        private String remark;

        private Map<String, Object> fieldValues;
    }

    /** 修改备注（任意状态可改，含审批中/已定稿；备注不参与渲染与定稿快照） */
    @Data
    public static class RemarkRequest {
        @Size(max = 500, message = "备注最长 500 字")
        private String remark;
    }

    /** 打印请求（按所选纸张 1:1 重渲染） */
    @Data
    public static class PrintRequest {
        @Schema(description = "纸张 A4/A5/LETTER/LEGAL/CUSTOM，缺省用模板纸张")
        private String paperSize;

        @Schema(description = "自定义纸宽 mm（CUSTOM 时必填）")
        private java.math.BigDecimal customWidthMm;

        @Schema(description = "自定义纸高 mm（CUSTOM 时必填）")
        private java.math.BigDecimal customHeightMm;
    }

    /** 合同响应（列表不含 html/字段值大对象） */
    @Data
    public static class ContractResponse {
        private Long id;
        private String contractNo;
        private String title;
        private Long templateId;
        private String templateName;
        private String categoryCode;
        /** 装配：分类名称（ct_category） */
        private String categoryName;
        private Long templateVersionId;
        private Integer templateVersionNo;
        private Map<String, Object> fieldValues;
        private Integer status;
        private Long pdfAttachmentId;
        /** 定稿 PDF 直链（详情用，列表为空） */
        private String pdfFileUrl;
        private Long processInstanceId;
        private String processNo;
        /** 备注（管理性信息，任意状态可改） */
        private String remark;
        private Long createBy;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;

        public static ContractResponse from(CtContract c) {
            ContractResponse r = new ContractResponse();
            r.setId(c.getId());
            r.setContractNo(c.getContractNo());
            r.setTitle(c.getTitle());
            r.setTemplateId(c.getTemplateId());
            r.setTemplateVersionId(c.getTemplateVersionId());
            r.setFieldValues(c.getFieldValues());
            r.setStatus(c.getStatus());
            r.setPdfAttachmentId(c.getPdfAttachmentId());
            r.setProcessInstanceId(c.getProcessInstanceId());
            r.setProcessNo(c.getProcessNo());
            r.setRemark(c.getRemark());
            r.setCreateBy(c.getCreateBy());
            r.setCreateTime(c.getCreateTime());
            r.setUpdateTime(c.getUpdateTime());
            return r;
        }
    }
}
