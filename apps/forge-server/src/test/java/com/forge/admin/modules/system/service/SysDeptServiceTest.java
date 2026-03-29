package com.forge.admin.modules.system.service;

import com.forge.admin.ForgeAdminApplication;
import com.forge.admin.modules.system.dto.dept.DeptResponse;
import com.forge.admin.modules.system.dto.dept.DeptTreeResponse;
import com.forge.admin.modules.system.entity.SysDept;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 部门服务集成测试
 */
@SpringBootTest(classes = ForgeAdminApplication.class)
class SysDeptServiceTest {

    @Autowired
    private SysDeptService sysDeptService;

    @Test
    void testListDepts() {
        List<DeptResponse> depts = sysDeptService.listDepts(null, null);
        assertNotNull(depts);
        assertFalse(depts.isEmpty(), "应有部门数据");
    }

    @Test
    void testGetDeptTree() {
        List<DeptTreeResponse> tree = sysDeptService.getDeptTree();
        assertNotNull(tree);
        assertFalse(tree.isEmpty(), "部门树应非空");
    }

    @Test
    void testGetDeptDetail() {
        List<DeptResponse> depts = sysDeptService.listDepts(null, null);
        assertFalse(depts.isEmpty());

        Long deptId = depts.get(0).getId();
        DeptResponse detail = sysDeptService.getDeptDetail(deptId);
        assertNotNull(detail);
        assertNotNull(detail.getDeptName());
    }

    @Test
    void testGetChildDepts() {
        List<SysDept> allDepts = sysDeptService.list();
        assertFalse(allDepts.isEmpty());

        // 用顶级部门测试
        SysDept topDept = allDepts.stream()
                .filter(d -> d.getParentId() != null && d.getParentId() == 0)
                .findFirst()
                .orElse(null);

        if (topDept != null) {
            List<SysDept> children = sysDeptService.getChildDepts(topDept.getId());
            assertNotNull(children);
        }
    }
}
