package com.forge.modules.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 合同模板实体
 *
 * @author forge-admin
 */
@Data
@TableName("ct_template")
public class CtTemplate {

    /** 状态：草稿（未保存过内容版本） */
    public static final int STATUS_DRAFT = 0;
    /** 状态：启用（可被合同引用） */
    public static final int STATUS_ENABLED = 1;
    /** 状态：停用（仅禁新建，在途与已定稿合同不受影响） */
    public static final int STATUS_DISABLED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 合同分类ID（ct_category，仅启用叶子；叶子编码参与合同编号） */
    private Long categoryId;

    /** 模板名称（租户内唯一） */
    private String name;

    /** 当前版本ID（首版保存后回填，合同创建锁定该版本） */
    private Long currentVersionId;

    /** 状态 0:草稿 1:启用 2:停用 */
    private Integer status;

    /** 纸张 A4/A5/LETTER/LEGAL/CUSTOM */
    private String paperSize;

    /** 纸张方向 PORTRAIT:纵向 LANDSCAPE:横向 */
    private String paperOrientation;

    /** 页边距档位 NARROW:窄 NORMAL:正常 WIDE:宽 */
    private String marginPreset;

    /** 自定义纸宽 mm（CUSTOM 时有效） */
    private BigDecimal customWidthMm;

    /** 自定义纸高 mm（CUSTOM 时有效） */
    private BigDecimal customHeightMm;

    /** 备注 */
    private String remark;

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
