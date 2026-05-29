package com.forge.modules.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.common.exception.BusinessException;
import com.forge.common.response.ResultCode;
import com.forge.modules.workflow.dto.category.CategoryQueryRequest;
import com.forge.modules.workflow.dto.category.CategoryRequest;
import com.forge.modules.workflow.dto.category.CategoryResponse;
import com.forge.modules.workflow.entity.WfCategory;
import com.forge.modules.workflow.mapper.WfCategoryMapper;
import com.forge.modules.workflow.service.WfCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流程分类服务实现
 *
 * @author forge
 */
@Service
@RequiredArgsConstructor
public class WfCategoryServiceImpl extends ServiceImpl<WfCategoryMapper, WfCategory> implements WfCategoryService {

    private final WfCategoryMapper wfCategoryMapper;

    @Override
    public Page<WfCategory> pageCategory(CategoryQueryRequest request) {
        Page<WfCategory> page = new Page<>(request.getPageNum(), request.getPageSize());

        LambdaQueryWrapper<WfCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(request.getCategoryName()), WfCategory::getCategoryName, request.getCategoryName())
                .eq(StrUtil.isNotBlank(request.getCategoryCode()), WfCategory::getCategoryCode, request.getCategoryCode())
                .eq(request.getStatus() != null, WfCategory::getStatus, request.getStatus())
                .orderByAsc(WfCategory::getSortOrder)
                .orderByDesc(WfCategory::getCreateTime);

        return wfCategoryMapper.selectPage(page, wrapper);
    }

    @Override
    public List<CategoryResponse> listAll() {
        LambdaQueryWrapper<WfCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfCategory::getStatus, 1)
                .orderByAsc(WfCategory::getSortOrder);

        List<WfCategory> categories = wfCategoryMapper.selectList(wrapper);
        return buildTree(categories);
    }

    @Override
    public WfCategory getCategoryById(Long id) {
        WfCategory category = getById(id);
        if (category == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return category;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addCategory(CategoryRequest request) {
        // 检查分类编码是否已存在
        if (lambdaQuery().eq(WfCategory::getCategoryCode, request.getCategoryCode()).exists()) {
            throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "分类编码已存在");
        }

        WfCategory category = new WfCategory();
        BeanUtil.copyProperties(request, category);

        // 父级ID默认为0（顶级分类）
        if (category.getParentId() == null) {
            category.setParentId(0L);
        }

        save(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(CategoryRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

        WfCategory category = getById(request.getId());
        if (category == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }

        // 检查分类编码是否与其他分类重复
        if (!category.getCategoryCode().equals(request.getCategoryCode())) {
            if (lambdaQuery().eq(WfCategory::getCategoryCode, request.getCategoryCode()).exists()) {
                throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "分类编码已存在");
            }
        }

        // 不允许将分类设置为自己的子分类
        if (request.getParentId() != null && request.getParentId().equals(request.getId())) {
            throw new BusinessException("不能将分类设置为自己的子分类");
        }

        BeanUtil.copyProperties(request, category);
        updateById(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }

        // 检查是否存在子分类
        Long childCount = lambdaQuery()
                .in(WfCategory::getParentId, ids)
                .count();
        if (childCount > 0) {
            throw new BusinessException("存在子分类，不允许删除");
        }

        removeByIds(ids);
    }

    /**
     * 构建分类树形结构
     */
    private List<CategoryResponse> buildTree(List<WfCategory> categories) {
        List<CategoryResponse> responseList = categories.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        Map<Long, List<CategoryResponse>> groupedByParent = responseList.stream()
                .collect(Collectors.groupingBy(CategoryResponse::getParentId));

        responseList.forEach(response ->
                response.setChildren(groupedByParent.getOrDefault(response.getId(), new ArrayList<>()))
        );

        // 返回顶级分类（parentId = 0）
        return responseList.stream()
                .filter(response -> response.getParentId() == null || response.getParentId() == 0L)
                .collect(Collectors.toList());
    }

    /**
     * 实体转响应对象
     */
    private CategoryResponse convertToResponse(WfCategory category) {
        CategoryResponse response = new CategoryResponse();
        BeanUtil.copyProperties(category, response);
        return response;
    }
}
