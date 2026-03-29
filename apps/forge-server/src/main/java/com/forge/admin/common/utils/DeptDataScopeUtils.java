package com.forge.admin.common.utils;

import com.forge.admin.modules.system.entity.SysDept;
import com.forge.admin.modules.system.service.SysDeptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 部门数据权限工具类
 *
 * @author standadmin
 */
@Slf4j
public class DeptDataScopeUtils {

    /**
     * 部门树缓存
     * 格式: 父部门ID -> 子部门ID列表
     */
    private static final Map<Long, List<Long>> DEPT_TREE_CACHE = new ConcurrentHashMap<>();

    /**
     * 部门 ancestors 缓存
     * 格式: 部门ID -> ancestors 字符串
     */
    private static final Map<Long, String> DEPT_ANCESTORS_CACHE = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private SysDeptService sysDeptService;

    /**
     * 清空缓存
     */
    public static void clearCache() {
        DEPT_TREE_CACHE.clear();
        DEPT_ANCESTORS_CACHE.clear();
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

        // 缓存未初始化，返回只包含自己的列表
        // TODO: 在应用启动时调用 buildDeptTreeCache 初始化缓存
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
     * 需要在系统启动或部门变更时调用
     *
     * @param deptList 所有部门列表
     */
    public static void buildDeptTreeCache(List<DeptInfo> deptList) {
        clearCache();

        Map<Long, List<Long>> tempCache = new HashMap<>();

        for (DeptInfo dept : deptList) {
            // 缓存 ancestors
            DEPT_ANCESTORS_CACHE.put(dept.getId(), dept.getAncestors());

            // 构建父子关系
            Long parentId = dept.getParentId();
            if (parentId != null && parentId != 0) {
                tempCache.computeIfAbsent(parentId, k -> new ArrayList<>()).add(dept.getId());
            }
        }

        DEPT_TREE_CACHE.putAll(tempCache);
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
