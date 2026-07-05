package com.forge.modules.screen.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.screen.dto.ScreenCopyRequest;
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

    /**
     * 发布大屏：把草稿配置覆盖到正式配置，状态置为已发布，版本号 +1
     *
     * @param code 大屏编码
     */
    void publish(String code);

    /**
     * 复制大屏：拷贝源大屏的 config/config_draft/theme，新大屏状态置为草稿、版本号重置为 1
     *
     * @param sourceCode 源大屏编码
     * @param request    新大屏编码与名称
     * @return 新大屏 ID
     */
    Long copy(String sourceCode, ScreenCopyRequest request);
}
