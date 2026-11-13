package com.forge.modules.plugin.data;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.forge.common.response.PageResult;
import com.forge.framework.plugin.data.PluginDataApi;
import com.forge.framework.plugin.data.PluginDeptDTO;
import com.forge.framework.plugin.data.PluginUserDTO;
import com.forge.framework.plugin.data.PluginUserQuery;
import com.forge.modules.system.entity.SysDept;
import com.forge.modules.system.entity.SysUser;
import com.forge.modules.system.service.SysDeptService;
import com.forge.modules.system.service.SysUserService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 数据门面实现：委托 system 既有服务（读默认放行；写经 {@link PluginScopeChecker}）。
 *
 * <p>租户数据边界：门面运行在插件请求链路内（UserContext 携带租户），
 * system 服务的查询经 MP 租户插件自动收敛到本租户，无需额外过滤。
 *
 * @author forge-admin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginDataApiImpl implements PluginDataApi {

    private final SysUserService sysUserService;

    private final SysDeptService sysDeptService;

    private final PluginScopeChecker scopeChecker;

    @Override
    public List<PluginUserDTO> listUsers(String pluginCode, PluginUserQuery query) {
        return pageUsers(pluginCode, query, 1, 500).getList();
    }

    @Override
    public PageResult<PluginUserDTO> pageUsers(String pluginCode, PluginUserQuery query, int pageNum, int pageSize) {
        Page<SysUser> page = sysUserService.page(new Page<>(pageNum, pageSize),
                Wrappers.<SysUser>lambdaQuery()
                        .like(query != null && query.getUsername() != null && !query.getUsername().isBlank(),
                                SysUser::getUsername, query.getUsername())
                        .like(query != null && query.getNickname() != null && !query.getNickname().isBlank(),
                                SysUser::getNickname, query.getNickname())
                        .eq(query != null && query.getDeptId() != null, SysUser::getDeptId, query.getDeptId())
                        .eq(query != null && query.getStatus() != null, SysUser::getStatus, query.getStatus())
                        .orderByAsc(SysUser::getId));
        return PageResult.of(convertUsers(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<PluginDeptDTO> getDeptTree(String pluginCode) {
        List<SysDept> depts = sysDeptService.list(Wrappers.<SysDept>lambdaQuery()
                .orderByAsc(SysDept::getSortOrder));
        Map<Long, List<SysDept>> byParent = depts.stream()
                .collect(Collectors.groupingBy(SysDept::getParentId));
        return buildDeptTree(byParent, 0L);
    }

    @Override
    public void updateUserDept(String pluginCode, Long userId, Long deptId) {
        scopeChecker.check(pluginCode, "user:write");
        SysUser patch = new SysUser();
        patch.setId(userId);
        patch.setDeptId(deptId);
        sysUserService.updateById(patch);
        log.info("插件[{}]经门面调整员工部门: userId={} deptId={}", pluginCode, userId, deptId);
    }

    // ===== 内部 =====

    private List<PluginUserDTO> convertUsers(List<SysUser> users) {
        if (users.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, String> deptNames = sysDeptService.listByIds(users.stream()
                        .map(SysUser::getDeptId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName, (first, ignored) -> first));
        Function<SysUser, PluginUserDTO> mapper = user -> PluginUserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .phone(user.getPhone())
                .email(user.getEmail())
                .deptId(user.getDeptId())
                .deptName(deptNames.get(user.getDeptId()))
                .status(user.getStatus())
                .build();
        return users.stream().map(mapper).toList();
    }

    private List<PluginDeptDTO> buildDeptTree(Map<Long, List<SysDept>> byParent, Long parentId) {
        List<PluginDeptDTO> result = new ArrayList<>();
        for (SysDept dept : byParent.getOrDefault(parentId, List.of())) {
            PluginDeptDTO node = PluginDeptDTO.builder()
                    .id(dept.getId())
                    .parentId(dept.getParentId())
                    .deptName(dept.getDeptName())
                    .leader(dept.getLeader())
                    .phone(dept.getPhone())
                    .email(dept.getEmail())
                    .sortOrder(dept.getSortOrder())
                    .status(dept.getStatus())
                    .children(buildDeptTree(byParent, dept.getId()))
                    .build();
            result.add(node);
        }
        return result;
    }
}
