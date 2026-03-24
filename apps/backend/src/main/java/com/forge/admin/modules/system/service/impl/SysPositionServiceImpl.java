package com.forge.admin.modules.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.admin.common.exception.BusinessException;
import com.forge.admin.common.response.ResultCode;
import com.forge.admin.modules.system.dto.position.PositionQueryRequest;
import com.forge.admin.modules.system.dto.position.PositionRequest;
import com.forge.admin.modules.system.dto.position.PositionResponse;
import com.forge.admin.modules.system.entity.SysPosition;
import com.forge.admin.modules.system.mapper.SysPositionMapper;
import com.forge.admin.modules.system.service.SysPositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 岗位服务实现
 */
@Service
@RequiredArgsConstructor
public class SysPositionServiceImpl extends ServiceImpl<SysPositionMapper, SysPosition> implements SysPositionService {

    private final SysPositionMapper sysPositionMapper;

    @Override
    public Page<PositionResponse> pagePositions(PositionQueryRequest request) {
        Page<SysPosition> page = new Page<>(request.getPageNum(), request.getPageSize());

        // 使用带数据权限过滤的查询方法
        Page<SysPosition> positionPage = (Page<SysPosition>) sysPositionMapper.selectPositionPageWithPermission(
                page,
                request.getPositionName(),
                request.getPositionCode(),
                request.getStatus(),
                request.getDeptId()
        );

        Page<PositionResponse> responsePage = new Page<>();
        responsePage.setCurrent(positionPage.getCurrent());
        responsePage.setSize(positionPage.getSize());
        responsePage.setTotal(positionPage.getTotal());
        responsePage.setRecords(positionPage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList()));

        return responsePage;
    }

    @Override
    public List<PositionResponse> getAllPositions() {
        // 使用带数据权限过滤的查询方法
        return sysPositionMapper.selectPositionListWithPermission(
                        null,
                        null,
                        1,
                        null
                )
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PositionResponse getPositionDetail(Long id) {
        SysPosition position = getById(id);
        if (position == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return convertToResponse(position);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addPosition(PositionRequest request) {
        // 检查岗位编码是否存在
        if (lambdaQuery().eq(SysPosition::getPositionCode, request.getPositionCode()).exists()) {
            throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "岗位编码已存在");
        }

        SysPosition position = new SysPosition();
        BeanUtil.copyProperties(request, position);
        save(position);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePosition(PositionRequest request) {
        SysPosition position = getById(request.getId());
        if (position == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }

        // 检查岗位编码是否重复
        if (!position.getPositionCode().equals(request.getPositionCode())) {
            if (lambdaQuery().eq(SysPosition::getPositionCode, request.getPositionCode()).exists()) {
                throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "岗位编码已存在");
            }
        }

        BeanUtil.copyProperties(request, position);
        updateById(position);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePositions(List<Long> ids) {
        removeByIds(ids);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        SysPosition position = getById(id);
        if (position == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        position.setStatus(status);
        updateById(position);
    }

    private PositionResponse convertToResponse(SysPosition position) {
        PositionResponse response = new PositionResponse();
        BeanUtil.copyProperties(position, response);
        return response;
    }
}
