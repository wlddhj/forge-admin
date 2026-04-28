package com.forge.admin.modules.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.admin.common.exception.BusinessException;
import com.forge.admin.common.response.ResultCode;
import com.forge.admin.modules.system.dto.role.RoleExport;
import com.forge.admin.modules.system.dto.role.RoleQueryRequest;
import com.forge.admin.modules.system.dto.role.RoleRequest;
import com.forge.admin.modules.system.dto.role.RoleResponse;
import com.forge.admin.modules.system.entity.SysRole;
import com.forge.admin.modules.system.entity.SysRoleDept;
import com.forge.admin.modules.system.entity.SysRoleMenu;
import com.forge.admin.modules.system.mapper.SysRoleDeptMapper;
import com.forge.admin.modules.system.mapper.SysRoleMapper;
import com.forge.admin.modules.system.mapper.SysRoleMenuMapper;
import com.forge.admin.modules.system.mapper.SysUserRoleMapper;
import com.forge.admin.modules.system.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色服务实现
 *
 * @author standadmin
 */
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleDeptMapper sysRoleDeptMapper;

    @Override
    public Page<RoleResponse> pageRoles(RoleQueryRequest request) {
        Page<SysRole> page = new Page<>(request.getPageNum(), request.getPageSize());

        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(request.getRoleName()), SysRole::getRoleName, request.getRoleName())
                .like(StrUtil.isNotBlank(request.getRoleCode()), SysRole::getRoleCode, request.getRoleCode())
                .eq(request.getStatus() != null, SysRole::getStatus, request.getStatus())
                .orderByAsc(SysRole::getSortOrder);

        Page<SysRole> rolePage = sysRoleMapper.selectPage(page, wrapper);

        Page<RoleResponse> responsePage = new Page<>();
        responsePage.setCurrent(rolePage.getCurrent());
        responsePage.setSize(rolePage.getSize());
        responsePage.setTotal(rolePage.getTotal());
        responsePage.setRecords(rolePage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList()));

        return responsePage;
    }

    @Override
    public List<RoleResponse> getAllRoles() {
        return lambdaQuery().eq(SysRole::getStatus, 1)
                .orderByAsc(SysRole::getSortOrder)
                .list()
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public RoleResponse getRoleDetail(Long id) {
        SysRole role = getById(id);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        return convertToResponse(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addRole(RoleRequest request) {
        // 检查角色编码是否存在
        if (lambdaQuery().eq(SysRole::getRoleCode, request.getRoleCode()).exists()) {
            throw new BusinessException(ResultCode.ROLE_EXISTS);
        }

        SysRole role = new SysRole();
        BeanUtil.copyProperties(request, role);
        save(role);

        // 保存自定义数据权限的部门关联
        if ("2".equals(request.getDataScope()) && request.getDeptIds() != null && !request.getDeptIds().isEmpty()) {
            saveRoleDepts(role.getId(), request.getDeptIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(RoleRequest request) {
        SysRole role = getById(request.getId());
        if (role == null) {
            throw new BusinessException("角色不存在");
        }

        // 固定角色只能修改部分字段
        if (role.getIsFixed() != null && role.getIsFixed() == 1) {
            // 固定角色只能修改：数据权限、状态、描述
            role.setDataScope(request.getDataScope());
            role.setStatus(request.getStatus());
            role.setDescription(request.getDescription());
        } else {
            // 非固定角色可以修改所有字段
            // 检查角色编码是否重复
            if (!role.getRoleCode().equals(request.getRoleCode())) {
                if (lambdaQuery().eq(SysRole::getRoleCode, request.getRoleCode()).exists()) {
                    throw new BusinessException(ResultCode.ROLE_EXISTS);
                }
            }
            BeanUtil.copyProperties(request, role);
        }

        updateById(role);

        // 更新自定义数据权限的部门关联
        sysRoleDeptMapper.deleteByRoleId(role.getId());
        if ("2".equals(request.getDataScope()) && request.getDeptIds() != null && !request.getDeptIds().isEmpty()) {
            saveRoleDepts(role.getId(), request.getDeptIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        // 检查是否分配给用户
        // 这里简化处理，实际需要检查关联
        removeById(id);
        sysRoleMenuMapper.deleteByRoleId(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRoles(List<Long> ids) {
        removeByIds(ids);
        ids.forEach(sysRoleMenuMapper::deleteByRoleId);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        SysRole role = getById(id);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        role.setStatus(status);
        updateById(role);
    }

    @Override
    public List<Long> getRoleMenuIds(Long roleId) {
        return sysRoleMenuMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId)
        ).stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "menu", allEntries = true)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        // 删除原有菜单关联
        sysRoleMenuMapper.deleteByRoleId(roleId);

        // 保存新的菜单关联
        if (menuIds != null && !menuIds.isEmpty()) {
            List<SysRoleMenu> roleMenus = menuIds.stream()
                    .map(menuId -> {
                        SysRoleMenu roleMenu = new SysRoleMenu();
                        roleMenu.setRoleId(roleId);
                        roleMenu.setMenuId(menuId);
                        return roleMenu;
                    })
                    .collect(Collectors.toList());
            sysRoleMenuMapper.batchInsert(roleMenus);
        }
    }

    private RoleResponse convertToResponse(SysRole role) {
        RoleResponse response = new RoleResponse();
        BeanUtil.copyProperties(role, response);
        // 查询自定义数据权限的部门列表
        if ("2".equals(role.getDataScope())) {
            response.setDeptIds(sysRoleDeptMapper.selectDeptIdsByRoleId(role.getId()));
        }
        return response;
    }

    /**
     * 保存角色部门关联
     *
     * @param roleId  角色ID
     * @param deptIds 部门ID列表
     */
    private void saveRoleDepts(Long roleId, List<Long> deptIds) {
        List<SysRoleDept> roleDepts = deptIds.stream()
                .map(deptId -> {
                    SysRoleDept roleDept = new SysRoleDept();
                    roleDept.setRoleId(roleId);
                    roleDept.setDeptId(deptId);
                    return roleDept;
                })
                .collect(Collectors.toList());
        sysRoleDeptMapper.batchInsert(roleDepts);
    }

    @Override
    public List<RoleExport> getExportList(RoleQueryRequest request) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(request.getRoleName()), SysRole::getRoleName, request.getRoleName())
                .like(StrUtil.isNotBlank(request.getRoleCode()), SysRole::getRoleCode, request.getRoleCode())
                .eq(request.getStatus() != null, SysRole::getStatus, request.getStatus())
                .orderByAsc(SysRole::getSortOrder);

        List<SysRole> roles = list(wrapper);

        return roles.stream().map(role -> {
            RoleExport export = new RoleExport();
            export.setRoleName(role.getRoleName());
            export.setRoleCode(role.getRoleCode());
            export.setDescription(role.getDescription());
            export.setStatus(role.getStatus() == 1 ? "启用" : "禁用");
            export.setCreateTime(role.getCreateTime());
            return export;
        }).collect(Collectors.toList());
    }
}
