package com.forge.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.forge.admin.modules.system.dto.notice.NoticeQueryRequest;
import com.forge.admin.modules.system.dto.notice.NoticeRequest;
import com.forge.admin.modules.system.dto.notice.NoticeResponse;
import com.forge.admin.modules.system.entity.SysNotice;

import java.util.List;

/**
 * 通知公告服务接口
 */
public interface SysNoticeService extends IService<SysNotice> {

    /**
     * 分页查询通知公告
     */
    Page<NoticeResponse> pageNotices(NoticeQueryRequest request);

    /**
     * 获取公告详情
     */
    NoticeResponse getNoticeDetail(Long id);

    /**
     * 新增公告
     */
    void addNotice(NoticeRequest request, Long userId);

    /**
     * 更新公告
     */
    void updateNotice(NoticeRequest request);

    /**
     * 删除公告
     */
    void deleteNotices(Long[] ids);

    /**
     * 获取最新公告列表
     */
    List<NoticeResponse> getLatestNotices(Integer limit);
}
