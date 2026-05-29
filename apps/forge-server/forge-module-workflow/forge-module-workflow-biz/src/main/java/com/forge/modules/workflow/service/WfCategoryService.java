package com.forge.modules.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.workflow.dto.category.CategoryQueryRequest;
import com.forge.modules.workflow.dto.category.CategoryRequest;
import com.forge.modules.workflow.dto.category.CategoryResponse;
import com.forge.modules.workflow.entity.WfCategory;

import java.util.List;

/**
 * 流程分类服务接口
 *
 * @author forge
 */
public interface WfCategoryService {

    /**
     * 分页查询流程分类
     *
     * @param request 查询参数
     * @return 分页结果
     */
    Page<WfCategory> pageCategory(CategoryQueryRequest request);

    /**
     * 获取所有启用的分类（用于下拉选择）
     *
     * @return 分类列表
     */
    List<CategoryResponse> listAll();

    /**
     * 获取分类详情
     *
     * @param id 分类ID
     * @return 分类详情
     */
    WfCategory getCategoryById(Long id);

    /**
     * 新增分类
     *
     * @param request 分类请求
     */
    void addCategory(CategoryRequest request);

    /**
     * 更新分类
     *
     * @param request 分类请求
     */
    void updateCategory(CategoryRequest request);

    /**
     * 删除分类
     *
     * @param ids 分类ID列表
     */
    void deleteCategory(List<Long> ids);
}
