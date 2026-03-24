package com.forge.admin.modules.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.modules.system.dto.position.PositionQueryRequest;
import com.forge.admin.modules.system.dto.position.PositionRequest;
import com.forge.admin.modules.system.dto.position.PositionResponse;

import java.util.List;

/**
 * 岗位服务接口
 */
public interface SysPositionService {

    /**
     * 分页查询岗位
     */
    Page<PositionResponse> pagePositions(PositionQueryRequest request);

    /**
     * 获取所有岗位
     */
    List<PositionResponse> getAllPositions();

    /**
     * 获取岗位详情
     */
    PositionResponse getPositionDetail(Long id);

    /**
     * 新增岗位
     */
    void addPosition(PositionRequest request);

    /**
     * 更新岗位
     */
    void updatePosition(PositionRequest request);

    /**
     * 删除岗位
     */
    void deletePositions(List<Long> ids);

    /**
     * 更新状态
     */
    void updateStatus(Long id, Integer status);
}
