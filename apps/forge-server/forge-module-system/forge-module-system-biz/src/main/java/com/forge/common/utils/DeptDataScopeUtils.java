package com.forge.common.utils;

import com.forge.modules.system.entity.SysDept;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门数据权限工具类。
 * 缓存为跨租户全量快照（deptId 全局唯一，混合树不影响 per-dept 子树查询），
 * 以 volatile 不可变 Map 原子换引用——重建期间读者要么见旧完整快照要么见新完整快照，
 * 不存在部分树窗口（数据权限场景下部分树可能导致漏授权）
 *
 * @author standadmin
 */
@Slf4j
public class DeptDataScopeUtils {

    /**
     * 部门树缓存
     * 格式: 父部门ID -> 子部门ID列表
     */
    private static volatile Map<Long, List<Long>> DEPT_TREE_CACHE = Map.of();

    /**
     * 部门 ancestors 缓存
     * 格式: 部门ID -> ancestors 字符串
     */
    private static volatile Map<Long, String> DEPT_ANCESTORS_CACHE = Map.of();

    /**
     * 清空缓存
     */
    public static void clearCache() {
        DEPT_TREE_CACHE = Map.of();
        DEPT_ANCESTORS_CACHE = Map.of();
    }

    /**
     * 获取指定部门的所有子部门ID（包括自己）
     *
     * @param deptId 部门ID
     * @return 子部门ID列表
     */
    public static List<Long> getChildDeptIds(Long deptId) {
        if (deptId == null) {
            return Collections.emptyList();
        }

        // 从缓存获取
        List<Long> cached = DEPT_TREE_CACHE.get(deptId);
        if (cached != null && !cached.isEmpty()) {
            List<Long> result = new ArrayList<>();
            result.add(deptId);
            collectChildDeptIds(deptId, result);
            return result;
        }

        // 缓存未初始化（可能初始化失败），返回只包含自己的列表
        log.warn("部门树缓存未初始化，deptId={}，返回只包含自己的列表", deptId);
        return Collections.singletonList(deptId);
    }

    /**
     * 递归收集子部门ID
     *
     * @param parentId 父部门ID
     * @param result 结果列表
     */
    private static void collectChildDeptIds(Long parentId, List<Long> result) {
        // 从缓存获取子部门
        List<Long> childIds = DEPT_TREE_CACHE.get(parentId);
        if (childIds == null || childIds.isEmpty()) {
            return;
        }

        for (Long childId : childIds) {
            if (!result.contains(childId)) {
                result.add(childId);
                collectChildDeptIds(childId, result);
            }
        }
    }

    /**
     * 判断是否为子部门
     *
     * @param parentId 父部门ID
     * @param childId 子部门ID
     * @return 是否为子部门
     */
    public static boolean isChildDept(Long parentId, Long childId) {
        if (parentId == null || childId == null) {
            return false;
        }

        if (parentId.equals(childId)) {
            return true;
        }

        List<Long> childIds = getChildDeptIds(parentId);
        return childIds.contains(childId);
    }

    /**
     * 根据 ancestors 判断是否为子部门
     *
     * @param parentAncestors 父部门 ancestors
     * @param childAncestors 子部门 ancestors
     * @param childId 子部门ID
     * @return 是否为子部门
     */
    public static boolean isChildDeptByAncestors(String parentAncestors, String childAncestors, Long childId) {
        if (parentAncestors == null || childAncestors == null) {
            return false;
        }

        // 子部门的 ancestors 应该以父部门的 ancestors 开头
        // 例如：父部门 ancestors="0,1", 子部门 ancestors="0,1,2"
        return childAncestors.startsWith(parentAncestors);
    }

    /**
     * 构建部门树缓存
     * 需要在系统启动或部门变更时调用（须以跨租户全量部门列表传入）
     *
     * @param deptList 所有部门列表
     */
    public static void buildDeptTreeCache(List<DeptInfo> deptList) {
        Map<Long, List<Long>> tempTree = new HashMap<>();
        Map<Long, String> tempAncestors = new HashMap<>();

        for (DeptInfo dept : deptList) {
            tempAncestors.put(dept.getId(), dept.getAncestors());

            Long parentId = dept.getParentId();
            if (parentId != null && parentId != 0) {
                tempTree.computeIfAbsent(parentId, k -> new ArrayList<>()).add(dept.getId());
            }
        }

        // 构建完整后一次性换引用，读者无部分树窗口
        DEPT_ANCESTORS_CACHE = Map.copyOf(tempAncestors);
        DEPT_TREE_CACHE = Map.copyOf(tempTree);
        log.info("部门树缓存构建完成，共 {} 个部门", deptList.size());
    }

    /**
     * 部门信息（用于构建缓存）
     */
    public static class DeptInfo {
        private Long id;
        private Long parentId;
        private String ancestors;

        public DeptInfo(Long id, Long parentId, String ancestors) {
            this.id = id;
            this.parentId = parentId;
            this.ancestors = ancestors;
        }

        public Long getId() {
            return id;
        }

        public Long getParentId() {
            return parentId;
        }

        public String getAncestors() {
            return ancestors;
        }
    }
}
