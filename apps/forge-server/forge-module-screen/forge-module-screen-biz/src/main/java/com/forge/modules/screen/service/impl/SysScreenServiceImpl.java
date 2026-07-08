package com.forge.modules.screen.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.exception.BusinessException;
import com.forge.modules.screen.dto.ScreenCopyRequest;
import com.forge.modules.screen.dto.ScreenPageRequest;
import com.forge.modules.screen.dto.ScreenRequest;
import com.forge.modules.screen.dto.ScreenResponse;
import com.forge.modules.screen.entity.SysScreen;
import com.forge.modules.screen.enums.ScreenStatus;
import com.forge.modules.screen.mapper.SysScreenMapper;
import com.forge.modules.screen.service.ScreenAccessService;
import com.forge.modules.screen.service.SysScreenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 大屏管理服务实现
 *
 * @author standadmin
 */
@Service
@RequiredArgsConstructor
public class SysScreenServiceImpl implements SysScreenService {

    private final SysScreenMapper mapper;
    private final ScreenAccessService accessService;

    @Override
    public Page<ScreenResponse> page(ScreenPageRequest request) {
        Page<SysScreen> p = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<SysScreen> qw = new LambdaQueryWrapper<>();
        if (request.getName() != null && !request.getName().isBlank()) {
            qw.like(SysScreen::getName, request.getName());
        }
        if (request.getStatus() != null) {
            qw.eq(SysScreen::getStatus, request.getStatus());
        }
        qw.orderByDesc(SysScreen::getUpdateTime);

        Page<SysScreen> result = mapper.selectPage(p, qw);
        Page<ScreenResponse> out = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<ScreenResponse> records = result.getRecords().stream().map(this::toResponse).toList();
        out.setRecords(records);
        return out;
    }

    @Override
    public ScreenResponse getById(Long id) {
        SysScreen entity = mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("大屏不存在");
        }
        return toResponse(entity);
    }

    @Override
    public ScreenResponse getByCode(String code) {
        SysScreen entity = mapper.selectOne(
            new LambdaQueryWrapper<SysScreen>().eq(SysScreen::getCode, code));
        if (entity == null) {
            throw new BusinessException("大屏不存在: " + code);
        }
        // 仅已发布状态可通过运行时 API 访问
        if (entity.getStatus() == null || entity.getStatus() != ScreenStatus.PUBLISHED.getCode()) {
            throw new BusinessException("大屏未发布，无法访问");
        }
        // 非公开大屏需要登录用户访问
        if (entity.getIsPublic() == null || entity.getIsPublic() == 0) {
            org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                throw new BusinessException("大屏未公开，请登录后访问");
            }
            // accessType=1：检查当前用户的角色是否在授权列表中
            if (entity.getAccessType() != null && entity.getAccessType() == 1) {
                Set<Long> userRoleIds = currentUserRoleIds(auth);
                List<Long> allowed = accessService.listRoleIdsByScreenId(entity.getId());
                if (allowed.isEmpty() || userRoleIds.stream().noneMatch(allowed::contains)) {
                    throw new BusinessException("您没有访问该大屏的权限");
                }
            }
        }
        return toResponse(entity);
    }

    /**
     * 从 Spring Security 认证中提取当前用户角色 ID 列表。
     * 优先尝试反射调用 principal.getRoleIds()，回退到 GrantedAuthority 中 role: 前缀。
     */
    private Set<Long> currentUserRoleIds(org.springframework.security.core.Authentication auth) {
        if (auth == null) return Set.of();
        Object principal = auth.getPrincipal();
        // 反射调用 getRoleIds()（如果 principal 是 LoginUser）
        if (principal != null && !"anonymousUser".equals(principal)) {
            try {
                java.lang.reflect.Method m = principal.getClass().getMethod("getRoleIds");
                Object roleIds = m.invoke(principal);
                if (roleIds instanceof java.util.Collection<?> col) {
                    Set<Long> out = new java.util.HashSet<>();
                    for (Object o : col) {
                        if (o instanceof Number n) out.add(n.longValue());
                        else if (o != null) out.add(Long.parseLong(o.toString()));
                    }
                    return out;
                }
            } catch (Exception ignored) {
                // 反射失败时回退到 authorities 解析
            }
        }
        return auth.getAuthorities().stream()
            .map(org.springframework.security.core.GrantedAuthority::getAuthority)
            .filter(a -> a.startsWith("role:"))
            .map(a -> Long.parseLong(a.substring(5)))
            .collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public Long create(ScreenRequest request) {
        SysScreen entity = new SysScreen();
        BeanUtils.copyProperties(request, entity);
        entity.setStatus(ScreenStatus.DRAFT.getCode());
        entity.setVersion(1);
        entity.setTheme(request.getTheme() != null ? request.getTheme() : "dark-tech");
        if (entity.getIsPublic() == null) entity.setIsPublic(0);
        if (entity.getAccessType() == null) entity.setAccessType(0);
        mapper.insert(entity);
        if (entity.getAccessType() == 1 && request.getRoleIds() != null) {
            accessService.setScreenRoles(entity.getId(), request.getRoleIds());
        }
        return entity.getId();
    }

    @Override
    @Transactional
    public void update(ScreenRequest request) {
        if (request.getId() == null) {
            throw new BusinessException("ID 不能为空");
        }
        SysScreen entity = new SysScreen();
        BeanUtils.copyProperties(request, entity);
        if (request.getConfig() != null) {
            entity.setConfigDraft(request.getConfig());
        }
        mapper.updateById(entity);
        // accessType=1 时同步授权角色（前端传入完整 roleIds 列表）
        if (entity.getAccessType() != null && entity.getAccessType() == 1) {
            accessService.setScreenRoles(entity.getId(), request.getRoleIds() == null ? List.of() : request.getRoleIds());
        } else {
            // 切回"登录可访问"模式时清空授权
            accessService.setScreenRoles(entity.getId(), List.of());
        }
    }

    @Override
    @Transactional
    public void delete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (Long id : ids) {
            accessService.setScreenRoles(id, List.of());
        }
        mapper.deleteBatchIds(ids);
    }

    @Override
    @Transactional
    public void publish(String code) {
        SysScreen entity = mapper.selectOne(
            new LambdaQueryWrapper<SysScreen>().eq(SysScreen::getCode, code));
        if (entity == null) {
            throw new BusinessException("大屏不存在: " + code);
        }
        if (entity.getVersion() == null) {
            entity.setVersion(0);
        }
        entity.setConfig(entity.getConfigDraft());
        entity.setStatus(ScreenStatus.PUBLISHED.getCode());
        mapper.updateById(entity);
    }

    @Override
    @Transactional
    public Long copy(String sourceCode, ScreenCopyRequest request) {
        SysScreen src = mapper.selectOne(
            new LambdaQueryWrapper<SysScreen>().eq(SysScreen::getCode, sourceCode));
        if (src == null) {
            throw new BusinessException("源大屏不存在: " + sourceCode);
        }
        SysScreen dst = new SysScreen();
        dst.setCode(request.getNewCode());
        dst.setName(request.getNewName());
        dst.setConfig(src.getConfig());
        dst.setConfigDraft(src.getConfigDraft());
        dst.setTheme(src.getTheme());
        dst.setStatus(ScreenStatus.DRAFT.getCode());
        dst.setVersion(1);
        dst.setIsPublic(src.getIsPublic());
        dst.setAccessType(src.getAccessType());
        mapper.insert(dst);
        // 复制大屏的同时复制角色授权
        if (src.getAccessType() != null && src.getAccessType() == 1) {
            List<Long> srcRoles = accessService.listRoleIdsByScreenId(src.getId());
            accessService.setScreenRoles(dst.getId(), new ArrayList<>(srcRoles));
        }
        return dst.getId();
    }

    private ScreenResponse toResponse(SysScreen entity) {
        ScreenResponse resp = new ScreenResponse();
        BeanUtils.copyProperties(entity, resp);
        if (entity.getAccessType() != null && entity.getAccessType() == 1) {
            resp.setRoleIds(accessService.listRoleIdsByScreenId(entity.getId()));
        } else {
            resp.setRoleIds(new ArrayList<>());
        }
        return resp;
    }
}
