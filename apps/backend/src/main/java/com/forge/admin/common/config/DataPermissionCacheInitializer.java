package com.forge.admin.common.config;

import com.forge.admin.common.utils.DeptDataScopeUtils;
import com.forge.admin.modules.system.entity.SysDept;
import com.forge.admin.modules.system.service.SysDeptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * 数据权限缓存初始化器
 * 在应用启动时构建部门树缓存，确保 DEPT_AND_CHILD 数据权限类型正常工作
 *
 * @author standadmin
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "data-permission", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataPermissionCacheInitializer {

    private final SysDeptService sysDeptService;

    /**
     * 应用启动后初始化部门树缓存
     */
    @PostConstruct
    public void initializeDeptTreeCache() {
        try {
            log.info("开始初始化数据权限 - 部门树缓存...");

            // 查询所有部门
            List<SysDept> allDepts = sysDeptService.list();
            log.info("查询到 {} 个部门", allDepts.size());

            if (allDepts.isEmpty()) {
                log.warn("部门列表为空，跳过部门树缓存构建");
                return;
            }

            // 转换为 DeptInfo 并构建缓存
            List<DeptDataScopeUtils.DeptInfo> deptInfos = allDepts.stream()
                    .map(dept -> new DeptDataScopeUtils.DeptInfo(
                            dept.getId(),
                            dept.getParentId(),
                            dept.getAncestors()
                    ))
                    .toList();

            // 构建缓存
            DeptDataScopeUtils.buildDeptTreeCache(deptInfos);

            log.info("数据权限 - 部门树缓存初始化完成");

            // 输出部门树结构用于调试
            if (log.isDebugEnabled()) {
                log.debug("部门树结构:");
                for (SysDept dept : allDepts) {
                    log.debug("  部门[{}]: {}, 父部门={}, ancestors={}",
                            dept.getId(), dept.getDeptName(), dept.getParentId(), dept.getAncestors());
                }
            }

        } catch (Exception e) {
            log.error("初始化数据权限 - 部门树缓存失败", e);
            // 不抛出异常，允许应用继续启动
        }
    }
}
