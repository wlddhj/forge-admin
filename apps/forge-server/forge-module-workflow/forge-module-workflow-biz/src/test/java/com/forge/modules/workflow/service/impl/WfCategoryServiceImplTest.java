package com.forge.modules.workflow.service.impl;

import com.forge.common.exception.BusinessException;
import com.forge.modules.workflow.dto.category.CategoryRequest;
import com.forge.modules.workflow.entity.WfCategory;
import com.forge.modules.workflow.mapper.WfCategoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

/**
 * 流程分类树形化环校验单元测试
 *
 * 覆盖：子孙集合纯函数（层级/叶子/脏数据/环自救）、编辑父级成环拒绝、合法父级放行
 */
class WfCategoryServiceImplTest {

    private WfCategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = Mockito.spy(new WfCategoryServiceImpl(Mockito.mock(WfCategoryMapper.class)));
    }

    private WfCategory category(Long id, Long parentId) {
        WfCategory category = new WfCategory();
        category.setId(id);
        category.setParentId(parentId);
        category.setCategoryName("分类" + id);
        category.setCategoryCode("CAT_" + id);
        return category;
    }

    @Test
    @DisplayName("子孙集合：三层链路全量命中，叶子与无关节点不命中")
    void collectDescendantIdsShouldWalkHierarchy() {
        // 1 → 2 → 3，4 独立顶级
        List<WfCategory> all = List.of(
                category(1L, 0L), category(2L, 1L), category(3L, 2L), category(4L, 0L)
        );

        assertEquals(Set.of(2L, 3L), WfCategoryServiceImpl.collectDescendantIds(all, 1L));
        assertEquals(Set.of(3L), WfCategoryServiceImpl.collectDescendantIds(all, 2L));
        assertTrue(WfCategoryServiceImpl.collectDescendantIds(all, 3L).isEmpty());
        assertTrue(WfCategoryServiceImpl.collectDescendantIds(all, 99L).isEmpty());
    }

    @Test
    @DisplayName("子孙集合：parentId 为 null 的脏数据被过滤，互指环不死循环")
    void collectDescendantIdsShouldTolerateDirtyData() {
        List<WfCategory> all = List.of(
                category(1L, null), category(2L, 1L), category(3L, 2L), category(4L, 3L)
        );

        assertEquals(Set.of(2L, 3L, 4L), WfCategoryServiceImpl.collectDescendantIds(all, 1L));
        // 4 → 2 互指环：已入集合的节点不重复入队，遍历可终止
        List<WfCategory> cyclic = List.of(
                category(2L, 4L), category(3L, 2L), category(4L, 3L)
        );
        assertEquals(Set.of(3L, 4L), WfCategoryServiceImpl.collectDescendantIds(cyclic, 2L));
    }

    @Test
    @DisplayName("编辑：父级为自己的子孙应抛业务异常且不落库")
    void updateCategoryShouldRejectCycle() {
        WfCategory existing = category(1L, 0L);
        doReturn(existing).when(service).getById(1L);
        doReturn(List.of(category(1L, 0L), category(2L, 1L), category(3L, 2L))).when(service).list();

        CategoryRequest request = new CategoryRequest();
        request.setId(1L);
        request.setCategoryName("分类1");
        request.setCategoryCode("CAT_1");
        request.setParentId(3L);

        assertThrows(BusinessException.class, () -> service.updateCategory(request));
        verify(service, Mockito.never()).updateById(any(WfCategory.class));
    }

    @Test
    @DisplayName("编辑：父级为顶级或无关分类应放行，parentId 缺省归一为 0")
    void updateCategoryShouldAllowLegalParent() {
        WfCategory existing = category(3L, 2L);
        doReturn(existing).when(service).getById(3L);
        doReturn(List.of(category(1L, 0L), category(2L, 1L), existing)).when(service).list();
        doReturn(true).when(service).updateById(any(WfCategory.class));

        // 挂到顶级
        CategoryRequest topRequest = new CategoryRequest();
        topRequest.setId(3L);
        topRequest.setCategoryName("分类3");
        topRequest.setCategoryCode("CAT_3");
        topRequest.setParentId(0L);
        service.updateCategory(topRequest);

        // 不传父级 → 归一为顶级
        CategoryRequest nullParentRequest = new CategoryRequest();
        nullParentRequest.setId(3L);
        nullParentRequest.setCategoryName("分类3");
        nullParentRequest.setCategoryCode("CAT_3");
        service.updateCategory(nullParentRequest);

        verify(service, Mockito.times(2)).updateById(any(WfCategory.class));
        assertEquals(0L, existing.getParentId());
    }
}
