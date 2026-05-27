package com.forge.admin.modules.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.admin.common.exception.BusinessException;
import com.forge.admin.common.response.ResultCode;
import com.forge.admin.modules.workflow.dto.listener.ListenerQueryRequest;
import com.forge.admin.modules.workflow.dto.listener.ListenerRequest;
import com.forge.admin.modules.workflow.dto.listener.ListenerResponse;
import com.forge.admin.modules.workflow.entity.WfProcessListener;
import com.forge.admin.modules.workflow.mapper.WfProcessListenerMapper;
import com.forge.admin.modules.workflow.service.WfProcessListenerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WfProcessListenerServiceImpl extends ServiceImpl<WfProcessListenerMapper, WfProcessListener>
        implements WfProcessListenerService {

    private final WfProcessListenerMapper listenerMapper;

    @Override
    public Page<ListenerResponse> pageListeners(ListenerQueryRequest request) {
        Page<WfProcessListener> page = new Page<>(request.getPageNum(), request.getPageSize());

        LambdaQueryWrapper<WfProcessListener> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(request.getName()), WfProcessListener::getName, request.getName())
                .eq(StrUtil.isNotBlank(request.getType()), WfProcessListener::getType, request.getType())
                .eq(request.getStatus() != null, WfProcessListener::getStatus, request.getStatus())
                .orderByDesc(WfProcessListener::getCreateTime);

        Page<WfProcessListener> resultPage = listenerMapper.selectPage(page, wrapper);

        Page<ListenerResponse> responsePage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        responsePage.setRecords(resultPage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList()));
        return responsePage;
    }

    @Override
    public List<ListenerResponse> listAllEnabled() {
        LambdaQueryWrapper<WfProcessListener> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfProcessListener::getStatus, 1)
                .orderByDesc(WfProcessListener::getCreateTime);
        return listenerMapper.selectList(wrapper).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ListenerResponse getListenerDetail(Long id) {
        WfProcessListener listener = getById(id);
        if (listener == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return convertToResponse(listener);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addListener(ListenerRequest request) {
        WfProcessListener listener = new WfProcessListener();
        BeanUtil.copyProperties(request, listener);
        if (listener.getStatus() == null) {
            listener.setStatus(1);
        }
        save(listener);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateListener(ListenerRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        WfProcessListener listener = getById(request.getId());
        if (listener == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        BeanUtil.copyProperties(request, listener);
        updateById(listener);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteListeners(List<Long> ids) {
        removeByIds(ids);
    }

    private ListenerResponse convertToResponse(WfProcessListener listener) {
        ListenerResponse response = new ListenerResponse();
        BeanUtil.copyProperties(listener, response);
        return response;
    }
}
