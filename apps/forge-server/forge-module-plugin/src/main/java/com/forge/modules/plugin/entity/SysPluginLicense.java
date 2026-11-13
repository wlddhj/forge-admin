package com.forge.modules.plugin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 插件 License 记录（全局共享表）。一码多插件的 License 导入时按 pluginCodes
 * 拆多行（同 license_key），行内 scopes 为该 License 内置数据 scope（CSV）。
 *
 * @author forge-admin
 */
@Data
@TableName("sys_plugin_license")
public class SysPluginLicense {

    /** 1有效 0已过期/已撤销 2已替换（新 License 导入自动顶替） */
    public static final int STATUS_ACTIVE = 1;

    public static final int STATUS_INVALID = 0;

    public static final int STATUS_REPLACED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** License 唯一编号（载荷内 licenseKey） */
    private String licenseKey;

    private Long tenantId;

    /** 冗余自载荷：租户 code，防租户删除后失去审计线索 */
    private String tenantCode;

    /** 被授权方名称（公司主体，展示用） */
    private String licenseeName;

    private String pluginCode;

    /** License 内置数据 scope（CSV，如 "user:write,dept:write"） */
    private String scopes;

    private LocalDateTime notBefore;

    private LocalDateTime notAfter;

    private LocalDateTime issuedAt;

    /** 1有效 0已过期/撤销 2已替换 */
    private Integer status;

    /** 最近巡检结果 1通过 0未通过 */
    private Integer verifyState;

    private String failReason;

    private LocalDateTime verifyTime;

    /** 载荷 canonical JSON 明文（复核用） */
    private String payloadText;

    /** Base64 签名（复核用） */
    private String signature;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createBy;

    private Long updateBy;

    /**
     * 墓碑列：delval 显式写列名 id——uk 含 deleted 列（uk_license）。
     */
    @TableLogic(delval = "id")
    private Long deleted;
}
