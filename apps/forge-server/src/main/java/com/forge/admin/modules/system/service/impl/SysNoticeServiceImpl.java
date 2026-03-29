package com.forge.admin.modules.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.admin.common.exception.BusinessException;
import com.forge.admin.common.response.ResultCode;
import com.forge.admin.modules.system.dto.notice.NoticeQueryRequest;
import com.forge.admin.modules.system.dto.notice.NoticeRequest;
import com.forge.admin.modules.system.dto.notice.NoticeResponse;
import com.forge.admin.modules.system.entity.SysNotice;
import com.forge.admin.modules.system.entity.SysUser;
import com.forge.admin.modules.system.mapper.SysNoticeMapper;
import com.forge.admin.modules.system.service.SysNoticeService;
import com.forge.admin.modules.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 通知公告服务实现
 */
@Service
@RequiredArgsConstructor
public class SysNoticeServiceImpl extends ServiceImpl<SysNoticeMapper, SysNotice> implements SysNoticeService {

    private final SysNoticeMapper sysNoticeMapper;
    private final SysUserService sysUserService;

    @Override
    public Page<NoticeResponse> pageNotices(NoticeQueryRequest request) {
        Page<SysNotice> page = new Page<>(request.getPageNum(), request.getPageSize());

        LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(request.getNoticeTitle()), SysNotice::getNoticeTitle, request.getNoticeTitle())
                .eq(request.getNoticeType() != null, SysNotice::getNoticeType, request.getNoticeType())
                .eq(request.getStatus() != null, SysNotice::getStatus, request.getStatus())
                .orderByDesc(SysNotice::getCreateTime);

        Page<SysNotice> noticePage = sysNoticeMapper.selectPage(page, wrapper);

        Page<NoticeResponse> responsePage = new Page<>();
        responsePage.setCurrent(noticePage.getCurrent());
        responsePage.setSize(noticePage.getSize());
        responsePage.setTotal(noticePage.getTotal());
        responsePage.setRecords(noticePage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList()));

        return responsePage;
    }

    @Override
    public NoticeResponse getNoticeDetail(Long id) {
        SysNotice notice = getById(id);
        if (notice == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return convertToResponse(notice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addNotice(NoticeRequest request, Long userId) {
        SysNotice notice = new SysNotice();
        BeanUtil.copyProperties(request, notice);
        notice.setCreateBy(userId);
        notice.setCreateTime(LocalDateTime.now());
        save(notice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNotice(NoticeRequest request) {
        SysNotice notice = getById(request.getId());
        if (notice == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }

        BeanUtil.copyProperties(request, notice);
        notice.setUpdateTime(LocalDateTime.now());
        updateById(notice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNotices(Long[] ids) {
        removeByIds(Arrays.asList(ids));
    }

    @Override
    public List<NoticeResponse> getLatestNotices(Integer limit) {
        return lambdaQuery()
                .eq(SysNotice::getStatus, 1)
                .orderByDesc(SysNotice::getCreateTime)
                .last("LIMIT " + (limit != null ? limit : 5))
                .list()
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    private NoticeResponse convertToResponse(SysNotice notice) {
        NoticeResponse response = new NoticeResponse();
        BeanUtil.copyProperties(notice, response);

        // 获取创建者名称
        if (notice.getCreateBy() != null) {
            SysUser user = sysUserService.getById(notice.getCreateBy());
            if (user != null) {
                response.setCreateByName(user.getNickname());
            }
        }

        return response;
    }
}
