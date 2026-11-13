package com.forge.framework.plugin.data;

import lombok.Builder;
import lombok.Data;

/**
 * 插件视角的员工数据（脱敏至业务所需最小集，不含密码/登录策略等敏感字段）。
 *
 * @author forge-admin
 */
@Data
@Builder
public class PluginUserDTO {

    private Long id;

    private String username;

    private String nickname;

    private String phone;

    private String email;

    private Long deptId;

    private String deptName;

    /** 1启用 0禁用 */
    private Integer status;
}
