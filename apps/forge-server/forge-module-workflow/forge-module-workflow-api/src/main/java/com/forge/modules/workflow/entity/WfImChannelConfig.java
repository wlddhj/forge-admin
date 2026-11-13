package com.forge.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.forge.framework.mybatis.handler.EncryptTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IM 通知渠道配置（按租户维度：企业微信/钉钉每租户每渠道一行）
 * <p>
 * secret 经 EncryptTypeHandler AES-256-GCM 加密存储、读取自动解密；
 * 管理接口出参不回显明文（仅 hasSecret）。
 *
 * @author forge-admin
 */
@Data
@TableName(value = "wf_im_channel_config", autoResultMap = true)
public class WfImChannelConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID（非租户模式视作租户 1） */
    private Long tenantId;

    /** 渠道标识：wework / dingtalk */
    private String channel;

    /** 是否启用（0/1） */
    private Integer enabled;

    /** 企业微信 corpId（钉钉渠道为空） */
    private String corpId;

    /** 钉钉 appKey（企微渠道为空） */
    private String appKey;

    /** 应用 agentId */
    private String agentId;

    /** 应用 secret（AES-256-GCM 加密存储，读取自动解密） */
    @TableField(typeHandler = EncryptTypeHandler.class)
    private String secret;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 创建者 */
    private String createBy;

    /** 更新者 */
    private String updateBy;

    /**
     * 逻辑删除（墓碑惯用法）：有效行恒为 0；删除时置本行 id（配合
     * uk_tenant_channel(tenant_id, channel, deleted) 使同键多墓碑共存）。
     * delval 显式写列名 id——防止误用 MP 自带 deleteById 生成墓碑=1 撞唯一索引。
     */
    @TableLogic(delval = "id")
    private Long deleted;
}
