package com.forge.modules.screen.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.screen.dto.ScreenPageRequest;
import com.forge.modules.screen.dto.ScreenRequest;
import com.forge.modules.screen.dto.ScreenResponse;

import java.util.List;

/**
 * 大屏管理服务
 *
 * @author standadmin
 */
public interface SysScreenService {

    /**
     * 分页查询大屏
     */
    Page<ScreenResponse> page(ScreenPageRequest request);

    /**
     * 根据 ID 查询大屏
     */
    ScreenResponse getById(Long id);

    /**
     * 根据编码查询大屏
     */
    ScreenResponse getByCode(String code);

    /**
     * 新增大屏
     */
    Long create(ScreenRequest request);

    /**
     * 修改大屏
     */
    void update(ScreenRequest request);

    /**
     * 批量删除大屏
     */
    void delete(List<Long> ids);
}
