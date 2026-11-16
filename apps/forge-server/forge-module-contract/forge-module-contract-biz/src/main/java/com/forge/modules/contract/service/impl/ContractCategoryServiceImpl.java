package com.forge.modules.contract.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.forge.common.exception.BusinessException;
import com.forge.common.utils.SecurityUtils;
import com.forge.modules.contract.dto.CategoryDtos;
import com.forge.modules.contract.entity.CtCategory;
import com.forge.modules.contract.entity.CtTemplate;
import com.forge.modules.contract.mapper.CtCategoryMapper;
import com.forge.modules.contract.mapper.CtTemplateMapper;
import com.forge.modules.contract.service.ContractCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 合同分类服务实现（树形，范式参照 wf_category；差异：编码用户自定义参与合同编号、
 * 删除守卫额外覆盖模板挂载）
 *
 * @author forge-admin
 */
@Service
@RequiredArgsConstructor
public class ContractCategoryServiceImpl implements ContractCategoryService {

    private final CtCategoryMapper categoryMapper;
    private final CtTemplateMapper templateMapper;

    @Override
    public List<CtCategory> list(Integer status) {
        return categoryMapper.selectList(new LambdaQueryWrapper<CtCategory>()
                .eq(status != null, CtCategory::getStatus, status)
                .orderByAsc(CtCategory::getSortOrder)
                .orderByAsc(CtCategory::getId));
    }

    @Override
    public List<CategoryDtos.TreeNode> tree(Integer status) {
        return buildTree(list(status));
    }

    @Override
    public List<CategoryDtos.TreeNode> treeAll() {
        return tree(null);
    }

    @Override
    public Long addCategory(CategoryDtos.SaveRequest request) {
        validateParent(request.getParentId());
        CtCategory c = new CtCategory();
        c.setCategoryName(request.getCategoryName().trim());
        c.setCategoryCode(request.getCategoryCode().trim().toUpperCase());
        c.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        c.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        c.setStatus(request.getStatus() == null ? CtCategory.STATUS_ENABLED : request.getStatus());
        c.setRemark(request.getRemark());
        c.setCreateBy(SecurityUtils.getCurrentUserId());
        try {
            categoryMapper.insert(c);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(400, "分类编码「" + c.getCategoryCode() + "」已存在");
        }
        return c.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(CategoryDtos.SaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(400, "分类ID不能为空");
        }
        CtCategory exists = categoryMapper.selectById(request.getId());
        if (exists == null) {
            throw new BusinessException(404, "分类不存在");
        }
        validateParent(request.getParentId());
        // 防环：不允许挂到自身或其子孙下
        Long newParentId = request.getParentId() == null ? 0L : request.getParentId();
        if (!newParentId.equals(0L)) {
            Set<Long> forbidden = collectDescendantIds(request.getId());
            forbidden.add(request.getId());
            if (forbidden.contains(newParentId)) {
                throw new BusinessException(400, "不能选择自身或其子分类作为父级");
            }
        }
        CtCategory upd = new CtCategory();
        upd.setId(request.getId());
        upd.setCategoryName(request.getCategoryName().trim());
        upd.setCategoryCode(request.getCategoryCode().trim().toUpperCase());
        upd.setParentId(newParentId);
        upd.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        upd.setStatus(request.getStatus() == null ? exists.getStatus() : request.getStatus());
        upd.setRemark(request.getRemark());
        upd.setUpdateBy(SecurityUtils.getCurrentUserId());
        try {
            categoryMapper.updateById(upd);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(400, "分类编码「" + upd.getCategoryCode() + "」已存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<CtCategory>()
                .in(CtCategory::getParentId, ids).last("LIMIT 1"));
        if (childCount > 0) {
            throw new BusinessException(400, "存在子分类，不允许删除");
        }
        Long templateCount = templateMapper.selectCount(new LambdaQueryWrapper<CtTemplate>()
                .in(CtTemplate::getCategoryId, ids).last("LIMIT 1"));
        if (templateCount > 0) {
            throw new BusinessException(400, "分类下已挂载合同模板，不允许删除（可停用）");
        }
        categoryMapper.deleteByIds(ids);
    }

    @Override
    public Set<Long> collectDescendantIds(Long categoryId) {
        Set<Long> result = new HashSet<>();
        if (categoryId == null) {
            return result;
        }
        List<CtCategory> all = categoryMapper.selectList(null);
        Map<Long, List<CtCategory>> byParent = new LinkedHashMap<>();
        for (CtCategory c : all) {
            byParent.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
        }
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(categoryId);
        while (!stack.isEmpty()) {
            Long current = stack.pop();
            if (!result.add(current)) {
                continue;
            }
            for (CtCategory child : byParent.getOrDefault(current, List.of())) {
                stack.push(child.getId());
            }
        }
        return result;
    }

    // ---------- 私有工具 ----------

    /** 平铺转树（按 sort,id 序，父不存在的孤儿节点平铺到根） */
    private List<CategoryDtos.TreeNode> buildTree(List<CtCategory> all) {
        Set<Long> parentIds = new HashSet<>();
        for (CtCategory c : all) {
            parentIds.add(c.getParentId());
        }
        Map<Long, List<CategoryDtos.TreeNode>> byParent = new LinkedHashMap<>();
        for (CtCategory c : all) {
            boolean leaf = !parentIds.contains(c.getId());
            byParent.computeIfAbsent(c.getParentId(), k -> new ArrayList<>())
                    .add(CategoryDtos.TreeNode.from(c, leaf));
        }
        List<CategoryDtos.TreeNode> roots = new ArrayList<>(byParent.getOrDefault(0L, List.of()));
        attachChildren(roots, byParent);
        return roots;
    }

    private void attachChildren(List<CategoryDtos.TreeNode> nodes, Map<Long, List<CategoryDtos.TreeNode>> byParent) {
        for (CategoryDtos.TreeNode node : nodes) {
            List<CategoryDtos.TreeNode> children = byParent.getOrDefault(node.getId(), List.of());
            node.setChildren(children);
            attachChildren(children, byParent);
        }
    }

    private void validateParent(Long parentId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        if (categoryMapper.selectById(parentId) == null) {
            throw new BusinessException(404, "父分类不存在");
        }
    }
}
