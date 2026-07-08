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
import com.forge.modules.screen.service.SysScreenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 大屏管理服务实现
 *
 * @author standadmin
 */
@Service
@RequiredArgsConstructor
public class SysScreenServiceImpl implements SysScreenService {

    private final SysScreenMapper mapper;

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
        return toResponse(entity);
    }

    @Override
    @Transactional
    public Long create(ScreenRequest request) {
        SysScreen entity = new SysScreen();
        BeanUtils.copyProperties(request, entity);
        entity.setStatus(ScreenStatus.DRAFT.getCode());
        entity.setVersion(1);
        entity.setTheme(request.getTheme() != null ? request.getTheme() : "dark-tech");
        mapper.insert(entity);
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
    }

    @Override
    @Transactional
    public void delete(List<Long> ids) {
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
        // 防御：旧数据 / 复制导入可能 version 为 null，先归一为 0，
        // 由 {@code OptimisticLockerInnerInterceptor} 在 updateById 时 +1。
        if (entity.getVersion() == null) {
            entity.setVersion(0);
        }
        entity.setConfig(entity.getConfigDraft());
        entity.setStatus(ScreenStatus.PUBLISHED.getCode());
        // 不手动 +1：{@code OptimisticLockerInnerInterceptor}（已在 MybatisPlusConfig 注册）
        // 会自动在 SET 中 +1，并在 WHERE 中校验原 version，确保并发 publish 不相互覆盖。
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
        mapper.insert(dst);
        return dst.getId();
    }

    private ScreenResponse toResponse(SysScreen entity) {
        ScreenResponse resp = new ScreenResponse();
        BeanUtils.copyProperties(entity, resp);
        return resp;
    }
}
