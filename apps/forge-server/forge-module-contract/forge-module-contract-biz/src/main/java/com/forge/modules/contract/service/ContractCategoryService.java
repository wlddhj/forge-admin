package com.forge.modules.contract.service;

import com.forge.modules.contract.dto.CategoryDtos;

import java.util.List;
import java.util.Set;

/**
 * 合同分类服务（树形）
 *
 * @author forge-admin
 */
public interface ContractCategoryService {

    /** 分类树（status 可筛；leaf 标注叶子） */
    List<CategoryDtos.TreeNode> tree(Integer status);

    /** 全量平铺树（表单下拉数据源，含停用节点由前端标注禁选） */
    List<CategoryDtos.TreeNode> treeAll();

    /** 新建分类（编码租户内唯一守卫） */
    Long addCategory(CategoryDtos.SaveRequest request);

    /** 编辑分类（防环守卫；编码可改但校验唯一） */
    void updateCategory(CategoryDtos.SaveRequest request);

    /** 批量删除（守卫：有子分类或有模板挂载禁删） */
    void deleteCategory(List<Long> ids);

    /**
     * 收集节点及其全部子孙 id（列表「选父含子孙」筛选用）
     */
    Set<Long> collectDescendantIds(Long categoryId);
}
