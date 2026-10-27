package com.forge.modules.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
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
    public List<WfCategory> listCategory(CategoryQueryRequest request) {
        LambdaQueryWrapper<WfCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(request.getCategoryName()), WfCategory::getCategoryName, request.getCategoryName())
                .likeRight(StrUtil.isNotBlank(request.getCategoryCode()), WfCategory::getCategoryCode, request.getCategoryCode())
                .eq(request.getStatus() != null, WfCategory::getStatus, request.getStatus())
                .orderByAsc(WfCategory::getSortOrder)
                .orderByDesc(WfCategory::getCreateTime);

        return wfCategoryMapper.selectList(wrapper);
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
        // 分类编码自动生成（父编码+2位随机段），入参 code 一律忽略
        Long parentId = request.getParentId() == null ? 0L : request.getParentId();
        List<WfCategory> all = list();
        String parentCode = resolveParentCode(all, parentId);

        WfCategory category = new WfCategory();
        BeanUtil.copyProperties(request, category);
        category.setParentId(parentId);
        category.setCategoryCode(parentCode + generateSegment(usedSiblingSegments(all, parentId), ThreadLocalRandom.current()));
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

        Long oldParentId = category.getParentId();
        String oldCode = category.getCategoryCode();
        Long newParentId = request.getParentId() == null ? 0L : request.getParentId();

        List<WfCategory> all = list();
        String newParentCode = "";
        if (!newParentId.equals(0L)) {
            // 父级必须存在（不存在时抛业务异常）
            newParentCode = resolveParentCode(all, newParentId);
            // 不允许将分类挂到自身或其子孙分类下（会成环）
            Set<Long> forbidden = collectDescendantIds(all, request.getId());
            forbidden.add(request.getId());
            if (forbidden.contains(newParentId)) {
                throw new BusinessException("不能选择自身或其子分类作为父级");
            }
        }

        // 编码自动生成，copyProperties 后回填保护，防止入参 code 覆盖
        BeanUtil.copyProperties(request, category);
        category.setParentId(newParentId);
        category.setCategoryCode(oldCode);

        if (newParentId.equals(oldParentId)) {
            // 未移动：编码不变
            updateById(category);
            return;
        }

        // 移动：自身在新父下重新生成段，子孙编码做前缀级联替换
        Set<Long> descendantIds = collectDescendantIds(all, request.getId());
        List<WfCategory> descendants = all.stream()
                .filter(c -> descendantIds.contains(c.getId()))
                .collect(Collectors.toList());
        // 层级码不变式防御：子孙编码必须以旧编码为前缀（手工改库破坏时明确报错而非静默产出错码）
        for (WfCategory descendant : descendants) {
            if (descendant.getCategoryCode() == null || !descendant.getCategoryCode().startsWith(oldCode)) {
                throw new BusinessException("子分类「" + descendant.getCategoryName() + "」编码不符合层级规则，请先修复数据");
            }
        }

        String newCode = newParentCode + generateSegment(usedSiblingSegments(all, newParentId), ThreadLocalRandom.current());
        category.setCategoryCode(newCode);
        updateById(category);

        if (!descendants.isEmpty()) {
            descendants.forEach(c ->
                    c.setCategoryCode(newCode + c.getCategoryCode().substring(oldCode.length())));
            updateBatchById(descendants);
        }
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

    /** 分类编码字符集（数字+大写字母） */
    static final String CODE_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    /** 每级编码段长度 */
    static final int SEGMENT_LENGTH = 2;
    /** 同级随机段冲突重试上限（同级容量 36^2=2560，正常到不了上限） */
    static final int MAX_SEGMENT_RETRY = 64;

    /**
     * 解析父分类编码（顶级返回空串），父级不存在时抛业务异常
     */
    private String resolveParentCode(List<WfCategory> all, Long parentId) {
        if (parentId == null || parentId.equals(0L)) {
            return "";
        }
        return all.stream()
                .filter(c -> parentId.equals(c.getId()))
                .findFirst()
                .map(WfCategory::getCategoryCode)
                .orElseThrow(() -> new BusinessException("父分类不存在"));
    }

    /**
     * 同级（parentId 相同）已占用的编码段集合（code 末 SEGMENT_LENGTH 位）
     */
    static Set<String> usedSiblingSegments(List<WfCategory> all, Long parentId) {
        Long target = parentId == null ? 0L : parentId;
        return all.stream()
                .filter(c -> target.equals(c.getParentId()))
                .map(c -> c.getCategoryCode())
                .filter(code -> code != null && code.length() >= SEGMENT_LENGTH)
                .map(code -> code.substring(code.length() - SEGMENT_LENGTH))
                .collect(Collectors.toSet());
    }

    /**
     * 生成同级未占用的 2 位随机编码段（数字+大写字母）
     * 纯函数，随机源参数化便于单测复现
     *
     * @param used   同级已占用段集合
     * @param random 随机源（调用方传 ThreadLocalRandom.current()）
     * @return 未占用的 2 位段
     */
    static String generateSegment(Set<String> used, Random random) {
        for (int i = 0; i < MAX_SEGMENT_RETRY; i++) {
            StringBuilder segment = new StringBuilder(SEGMENT_LENGTH);
            for (int j = 0; j < SEGMENT_LENGTH; j++) {
                segment.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            if (!used.contains(segment.toString())) {
                return segment.toString();
            }
        }
        throw new BusinessException("同级分类数量已达上限");
    }

    /**
     * 计算指定分类的全部子孙ID集合（按 parentId 邻接关系广度遍历）
     * 纯函数，供环校验与单测直接使用
     *
     * @param all    全量分类（平铺）
     * @param rootId 起始分类ID
     * @return 不含 rootId 自身的子孙ID集合
     */
    static Set<Long> collectDescendantIds(List<WfCategory> all, Long rootId) {
        Map<Long, List<Long>> childrenByParent = all.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(WfCategory::getParentId,
                        Collectors.mapping(WfCategory::getId, Collectors.toList())));

        Set<Long> descendants = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>(childrenByParent.getOrDefault(rootId, List.of()));
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            // 脏数据互指环可能绕回根自身，子孙集合不含根
            if (!current.equals(rootId) && descendants.add(current)) {
                queue.addAll(childrenByParent.getOrDefault(current, List.of()));
            }
        }
        return descendants;
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
