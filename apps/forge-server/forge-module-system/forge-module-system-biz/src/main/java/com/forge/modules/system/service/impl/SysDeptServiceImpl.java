package com.forge.modules.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.forge.common.exception.BusinessException;
import com.forge.common.response.ResultCode;
import com.forge.common.utils.UserContext;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.system.dto.dept.DeptRequest;
import com.forge.modules.system.dto.dept.DeptResponse;
import com.forge.modules.system.dto.dept.DeptTreeResponse;
import com.forge.modules.system.entity.SysDept;
import com.forge.modules.system.mapper.SysDeptMapper;
import com.forge.modules.system.service.SysDeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 部门服务实现
 *
 * @author standadmin
 */
@Service
@RequiredArgsConstructor
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept> implements SysDeptService {

    private final SysDeptMapper sysDeptMapper;

    @Override
    public List<DeptResponse> listDepts(String deptName, Integer status) {
        LambdaQueryWrapper<SysDept> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(deptName), SysDept::getDeptName, deptName)
                .eq(status != null, SysDept::getStatus, status)
                .orderByAsc(SysDept::getSortOrder);

        List<SysDept> depts = list(wrapper);
        return depts.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    @Override
    // 不缓存:平台超管切换租户场景下,所有租户共享 key="'tree'" 会导致数据串台
    // 每次按当前 TenantContextHolder.tenantId 查(MP 自动注入)
    public List<DeptTreeResponse> getDeptTree() {
        List<SysDept> depts = lambdaQuery()
                .eq(SysDept::getStatus, 1)
                .orderByAsc(SysDept::getSortOrder)
                .list();
        return buildDeptTree(depts, 0L);
    }

    @Override
    public DeptResponse getDeptDetail(Long id) {
        SysDept dept = getById(id);
        if (dept == null) {
            throw new BusinessException("部门不存在");
        }
        return convertToResponse(dept);
    }

    @Override
    @CacheEvict(value = "dept", allEntries = true)
    public void addDept(DeptRequest request) {
        // 使用 TenantContextHolder.tenantId（来自 X-Tenant-Id header，当前操作租户）
        // 而不是 UserContext.tenantId（来自 JWT，平台超管切换租户时仍是其所属租户）
        // 平台超管切换租户场景: header=acme(3), JWT=default(1), 用 header 才能正确操作 acme
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("无法识别当前租户，请检查请求头 X-Tenant-Id");
        }

        SysDept dept = new SysDept();
        BeanUtil.copyProperties(request, dept);

        // tenantId 由 MyBatis Plus TenantLineInnerInterceptor 从 TenantContextHolder 自动注入

        // 设置祖级列表
        if (request.getParentId() != null && request.getParentId() > 0) {
            // getById 触发 MP 拦截器按 TenantContextHolder.tenantId(=header) 注入 sys_dept.tenant_id
            // 跨租户场景 parent 查不到 (null),必须拒绝,避免 parent_id 指向其他租户数据
            SysDept parent = getById(request.getParentId());
            if (parent == null) {
                throw new BusinessException("父部门不存在或不属于当前租户");
            }
            // 校验父部门属于当前操作租户（不是平台超管个人所属租户）
            if (!tenantId.equals(parent.getTenantId())) {
                throw new BusinessException("父部门不属于当前租户");
            }
            dept.setAncestors(parent.getAncestors() + "," + parent.getId());
        } else {
            dept.setParentId(0L);
            dept.setAncestors("0");
        }

        save(dept);
    }

    @Override
    @CacheEvict(value = "dept", allEntries = true)
    public void updateDept(DeptRequest request) {
        SysDept dept = getById(request.getId());
        if (dept == null) {
            throw new BusinessException("部门不存在");
        }
        // 跨租户防护：普通用户只能改本租户部门；平台超管（accountType==2）可改任意租户
        Long currentTenantId = UserContext.get().getTenantId();
        UserContext currentUser = UserContext.get();
        boolean isPlatformAdmin = currentUser != null && currentUser.isPlatformAdmin();
        if (!isPlatformAdmin && currentTenantId != null
                && !currentTenantId.equals(dept.getTenantId())) {
            throw new BusinessException("无权修改其他租户的部门");
        }
        BeanUtil.copyProperties(request, dept);
        // tenantId 不允许通过 updateDept 变更（防止跨租户迁移），由 TenantLineInnerInterceptor 自动保持
        updateById(dept);
    }

    @Override
    @CacheEvict(value = "dept", allEntries = true)
    public void deleteDept(Long id) {
        // 检查是否存在子部门
        if (sysDeptMapper.hasChildren(id) > 0) {
            throw new BusinessException(ResultCode.DEPT_HAS_CHILDREN);
        }
        // 检查部门下是否存在用户
        if (sysDeptMapper.hasUsers(id) > 0) {
            throw new BusinessException(ResultCode.DEPT_HAS_USERS);
        }
        removeById(id);
    }

    private List<DeptTreeResponse> buildDeptTree(List<SysDept> depts, Long parentId) {
        List<DeptTreeResponse> tree = new ArrayList<>();

        Map<Long, List<SysDept>> deptMap = depts.stream()
                .collect(Collectors.groupingBy(SysDept::getParentId));

        List<SysDept> parentDepts = deptMap.getOrDefault(parentId, new ArrayList<>());
        for (SysDept dept : parentDepts) {
            DeptTreeResponse node = convertToTreeResponse(dept);
            node.setChildren(buildDeptTree(depts, dept.getId()));
            tree.add(node);
        }

        return tree;
    }

    private DeptResponse convertToResponse(SysDept dept) {
        DeptResponse response = new DeptResponse();
        BeanUtil.copyProperties(dept, response);
        return response;
    }

    private DeptTreeResponse convertToTreeResponse(SysDept dept) {
        DeptTreeResponse response = new DeptTreeResponse();
        BeanUtil.copyProperties(dept, response);
        return response;
    }

    @Override
    public List<SysDept> getChildDepts(Long parentId) {
        List<SysDept> result = new ArrayList<>();
        // 获取所有部门
        List<SysDept> allDepts = lambdaQuery()
                .eq(SysDept::getStatus, 1)
                .list();

        // 递归获取子部门
        collectChildDepts(allDepts, parentId, result);
        return result;
    }

    /**
     * 递归收集子部门
     */
    private void collectChildDepts(List<SysDept> allDepts, Long parentId, List<SysDept> result) {
        for (SysDept dept : allDepts) {
            if (parentId.equals(dept.getParentId())) {
                result.add(dept);
                collectChildDepts(allDepts, dept.getId(), result);
            }
        }
    }
}
