package com.forge.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.forge.admin.modules.system.dto.role.RoleExport;
import com.forge.admin.modules.system.dto.role.RoleQueryRequest;
import com.forge.admin.modules.system.dto.role.RoleRequest;
import com.forge.admin.modules.system.dto.role.RoleResponse;
import com.forge.admin.modules.system.entity.SysRole;

import java.util.List;

/**
 * 角色服务接口
 *
 * @author standadmin
 */
public interface SysRoleService extends IService<SysRole> {

    /**
     * 分页查询角色
     */
    Page<RoleResponse> pageRoles(RoleQueryRequest request);

    /**
     * 获取所有角色
     */
    List<RoleResponse> getAllRoles();

    /**
     * 获取角色详情
     */
    RoleResponse getRoleDetail(Long id);

    /**
     * 新增角色
     */
    void addRole(RoleRequest request);

    /**
     * 更新角色
     */
    void updateRole(RoleRequest request);

    /**
     * 删除角色
     */
    void deleteRole(Long id);

    /**
     * 批量删除角色
     */
    void deleteRoles(List<Long> ids);

    /**
     * 更新角色状态
     */
    void updateStatus(Long id, Integer status);

    /**
     * 获取角色菜单ID列表
     */
    List<Long> getRoleMenuIds(Long roleId);

    /**
     * 分配菜单权限
     */
    void assignMenus(Long roleId, List<Long> menuIds);

    /**
     * 获取角色导出列表
     */
    List<RoleExport> getExportList(RoleQueryRequest request);
}
