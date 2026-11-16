package com.forge.modules.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 合同记录实体
 *
 * @author forge-admin
 */
@Data
@TableName(value = "ct_contract", autoResultMap = true)
public class CtContract {

    /** 状态：草稿（可编辑；驳回/撤销重新编辑也回到此状态） */
    public static final int STATUS_DRAFT = 0;
    /** 状态：审批中（在途，仅事件回写可变更） */
    public static final int STATUS_APPROVING = 1;
    /** 状态：已通过（终态，已生成定稿快照与水印 PDF） */
    public static final int STATUS_APPROVED = 2;
    /** 状态：已驳回（可重新编辑再提交） */
    public static final int STATUS_REJECTED = 3;
    /** 状态：已撤销（可重新编辑再提交） */
    public static final int STATUS_CANCELED = 4;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 合同编号（终态通过时生成 CT-<分类码>-YYYYMMDD-XXXX） */
    private String contractNo;

    /** 合同标题 */
    private String title;

    /** 模板ID */
    private Long templateId;

    /** 模板版本ID（创建时锁定） */
    private Long templateVersionId;

    /** 变量值 JSON（key→值，Jackson3TypeHandler；列名显式对齐 field_values_json） */
    @TableField(value = "field_values_json", typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> fieldValues;

    /** 状态 0:草稿 1:审批中 2:已通过 3:已驳回 4:已撤销 */
    private Integer status;

    /** 定稿渲染快照（终态通过时冻结，模板后续改版不影响） */
    private String renderedHtml;

    /** 定稿水印 PDF 附件ID（sys_attachment） */
    private Long pdfAttachmentId;

    /** 流程实例ID（businessKey=contract:{id}） */
    private Long processInstanceId;

    /** 流程编号（发起回写） */
    private String processNo;

    /** 创建人 */
    private Long createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private Long updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除墓碑（唯一索引含 deleted，delval=id） */
    @TableLogic(delval = "id")
    private Long deleted;

}
