package com.forge.framework.plugin.data;

import com.forge.common.response.PageResult;

import java.util.List;

/**
 * 插件访问底座数据的统一门面——插件模块禁止直连 sys_user / sys_dept 的
 * Mapper 做任何操作（见 02 设计禁止事项）。
 *
 * <p>读操作（员工/组织）默认放行；写操作必须命中插件已声明且被授予的
 * scope（管理端勾选或 License 内置），未授予抛 SCOPE_DENIED 并记审计。
 * 实现由插件管理业务模块提供，委托 system 既有服务。
 *
 * @author forge-admin
 */
public interface PluginDataApi {

    /** 员工列表（读，默认放行；pluginCode 仅用于审计与写侧 scope 归因） */
    List<PluginUserDTO> listUsers(String pluginCode, PluginUserQuery query);

    /** 员工分页（读，默认放行） */
    PageResult<PluginUserDTO> pageUsers(String pluginCode, PluginUserQuery query, int pageNum, int pageSize);

    /** 组织树（读，默认放行） */
    List<PluginDeptDTO> getDeptTree(String pluginCode);

    /** 调整员工所属部门（写，需 scope: user:write） */
    void updateUserDept(String pluginCode, Long userId, Long deptId);
}
