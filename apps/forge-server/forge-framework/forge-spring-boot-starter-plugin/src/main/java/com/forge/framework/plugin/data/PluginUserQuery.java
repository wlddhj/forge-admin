package com.forge.framework.plugin.data;

import lombok.Builder;
import lombok.Data;

/**
 * 插件读取员工数据的查询条件（门面入参）。
 *
 * @author forge-admin
 */
@Data
@Builder
public class PluginUserQuery {

    private String username;

    private String nickname;

    private String phone;

    private Long deptId;

    /** 1启用 0禁用；null=不限 */
    private Integer status;
}
