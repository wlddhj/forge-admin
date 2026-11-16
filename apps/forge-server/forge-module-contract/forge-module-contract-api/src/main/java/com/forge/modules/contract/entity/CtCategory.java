package com.forge.modules.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 合同分类实体（树形，照抄 wf_category 范式；编码用户自定义、租户内唯一，
 * 叶子编码参与合同编号 CT-&lt;code&gt;-日期-序列）
 *
 * @author forge-admin
 */
@Data
@TableName("ct_category")
public class CtCategory {

    /** 状态：停用（从挂载候选树隐藏，已挂模板不受影响） */
    public static final int STATUS_DISABLED = 0;
    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类名称 */
    private String categoryName;

    /** 分类编码（租户内唯一） */
    private String categoryCode;

    /** 父分类ID（0=根） */
    private Long parentId;

    /** 排序 */
    private Integer sortOrder;

    /** 状态 0:停用 1:启用 */
    private Integer status;

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

    /** 租户ID（拦截器自动注入） */
    private Long tenantId;
}
