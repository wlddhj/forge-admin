package com.forge.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.forge.admin.modules.system.dto.dept.DeptRequest;
import com.forge.admin.modules.system.dto.dept.DeptResponse;
import com.forge.admin.modules.system.dto.dept.DeptTreeResponse;
import com.forge.admin.modules.system.entity.SysDept;

import java.util.List;

/**
 * 部门服务接口
 *
 * @author standadmin
 */
public interface SysDeptService extends IService<SysDept> {

    /**
     * 查询部门列表
     */
    List<DeptResponse> listDepts(String deptName, Integer status);

    /**
     * 获取部门树
     */
    List<DeptTreeResponse> getDeptTree();

    /**
     * 获取部门详情
     */
    DeptResponse getDeptDetail(Long id);

    /**
     * 新增部门
     */
    void addDept(DeptRequest request);

    /**
     * 更新部门
     */
    void updateDept(DeptRequest request);

    /**
     * 删除部门
     */
    void deleteDept(Long id);

    /**
     * 获取指定部门的所有子部门（递归）
     */
    List<SysDept> getChildDepts(Long parentId);
}
