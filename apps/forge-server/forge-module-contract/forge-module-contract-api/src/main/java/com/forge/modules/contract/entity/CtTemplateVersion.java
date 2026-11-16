package com.forge.modules.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 合同模板版本实体（content 即版本内容，可追溯可回滚）
 *
 * @author forge-admin
 */
@Data
@TableName("ct_template_version")
public class CtTemplateVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板ID */
    private Long templateId;

    /** 版本号（模板内自增） */
    private Integer versionNo;

    /** 模板内容 HTML（含 data-field 变量芯片） */
    private String contentHtml;

    /** 变量定义 JSON（List&lt;FieldDef&gt; 序列化，key/label/type/required/options） */
    private String fieldDefsJson;

    /** 版本变更说明 */
    private String changeLog;

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
