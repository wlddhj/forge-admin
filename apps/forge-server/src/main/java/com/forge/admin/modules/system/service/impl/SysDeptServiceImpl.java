package com.forge.admin.modules.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.admin.common.exception.BusinessException;
import com.forge.admin.common.response.ResultCode;
import com.forge.admin.modules.system.dto.dept.DeptRequest;
import com.forge.admin.modules.system.dto.dept.DeptResponse;
import com.forge.admin.modules.system.dto.dept.DeptTreeResponse;
import com.forge.admin.modules.system.entity.SysDept;
import com.forge.admin.modules.system.mapper.SysDeptMapper;
import com.forge.admin.modules.system.service.SysDeptService;
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
    @Cacheable(value = "dept", key = "'tree'", unless = "#result == null || #result.isEmpty()")
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
        SysDept dept = new SysDept();
        BeanUtil.copyProperties(request, dept);

        // 设置祖级列表
        if (request.getParentId() != null && request.getParentId() > 0) {
            SysDept parent = getById(request.getParentId());
            if (parent != null) {
                dept.setAncestors(parent.getAncestors() + "," + parent.getId());
            }
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
        BeanUtil.copyProperties(request, dept);
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
