package com.forge.admin.common.aspect;

import com.forge.admin.common.annotation.DataScope;
import com.forge.admin.common.utils.UserContext;
import com.forge.admin.modules.system.entity.SysUser;
import com.forge.admin.modules.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据权限切面
 * 自动为查询添加部门数据过滤条件
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DataScopeAspect {

    private final SysUserService sysUserService;

    @Before("@annotation(dataScope)")
    public void doBefore(JoinPoint point, DataScope dataScope) {
        handleDataScope(point, dataScope);
    }

    protected void handleDataScope(JoinPoint point, DataScope dataScope) {
        // 获取当前用户
        String username = UserContext.getCurrentUsername();
        if (username == null) {
            return;
        }

        SysUser user = sysUserService.getByUsername(username);
        if (user == null) {
            return;
        }

        // 如果是超级管理员，不过滤数据
        if (user.getAccountType() != null && user.getAccountType() == 1) {
            return;
        }

        // 获取用户角色，判断是否有数据权限
        // 这里简化处理：普通用户只能查看本部门及下级部门数据
        Long deptId = user.getDeptId();
        if (deptId == null) {
            return;
        }

        // 获取SQL参数对象
        Object params = point.getArgs()[0];
        if (params instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) params;
            // 添加数据权限过滤条件
            String sql = String.format(
                    " AND (%s.dept_id = %d OR %s.dept_id IN (SELECT id FROM sys_dept WHERE ancestors LIKE '%%%d%%'))",
                    dataScope.deptAlias(), deptId, dataScope.deptAlias(), deptId
            );
            map.put("dataScope", sql);
        } else {
            // 尝试通过反射设置 dataScope 字段
            try {
                Method setDataScope = params.getClass().getDeclaredMethod("setDataScope", String.class);
                String sql = String.format(
                        " AND (%s.dept_id = %d OR %s.dept_id IN (SELECT id FROM sys_dept WHERE ancestors LIKE '%%%d%%'))",
                        dataScope.deptAlias(), deptId, dataScope.deptAlias(), deptId
                );
                setDataScope.invoke(params, sql);
            } catch (Exception e) {
                log.debug("无法设置数据权限参数: {}", e.getMessage());
            }
        }
    }
}
