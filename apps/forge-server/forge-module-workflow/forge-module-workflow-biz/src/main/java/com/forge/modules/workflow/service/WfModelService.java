package com.forge.modules.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.workflow.dto.model.ModelQueryRequest;
import com.forge.modules.workflow.dto.model.ModelRequest;
import com.forge.modules.workflow.dto.model.ModelResponse;

/**
 * 流程模型管理服务
 *
 * @author forge-admin
 */
public interface WfModelService {

    /**
     * 分页查询模型
     *
     * @param request 查询条件
     * @return 分页结果
     */
    Page<ModelResponse> pageModel(ModelQueryRequest request);

    /**
     * 获取模型详情
     *
     * @param id 模型ID
     * @return 模型详情
     */
    ModelResponse getModelById(String id);

    /**
     * 创建模型
     *
     * @param request 模型请求
     */
    void createModel(ModelRequest request);

    /**
     * 更新模型
     *
     * @param request 模型请求
     */
    void updateModel(ModelRequest request);

    /**
     * 部署模型
     *
     * @param id 模型ID
     */
    void deployModel(String id);

    /**
     * 删除模型
     *
     * @param id 模型ID
     */
    void deleteModel(String id);
}
