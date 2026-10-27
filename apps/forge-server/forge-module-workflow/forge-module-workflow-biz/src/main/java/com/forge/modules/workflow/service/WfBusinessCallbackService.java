package com.forge.modules.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.workflow.dto.callback.CallbackRecordResponse;
import com.forge.modules.workflow.dto.callback.CallbackRequest;
import com.forge.modules.workflow.dto.callback.CallbackResponse;
import com.forge.modules.workflow.entity.WfCallbackRecord;

/**
 * 业务回调管理服务接口
 *
 * @author forge-admin
 */
public interface WfBusinessCallbackService {

    /**
     * 分页查询回调配置
     */
    Page<CallbackResponse> pageCallback(int pageNum, int pageSize, String name, Integer enabled);

    /**
     * 新增回调配置
     */
    void createCallback(CallbackRequest request);

    /**
     * 编辑回调配置
     */
    void updateCallback(CallbackRequest request);

    /**
     * 删除回调配置
     */
    void deleteCallback(Long id);

    /**
     * 分页查询投递记录
     */
    Page<CallbackRecordResponse> pageRecords(int pageNum, int pageSize, Long callbackId, String status);

    /**
     * 手动重发失败的投递记录（SUCCESS 记录拒绝）
     */
    void resendRecord(Long recordId);

    /**
     * 内部：按 id 取投递记录实体（重发用）
     */
    WfCallbackRecord getRecord(Long recordId);
}
