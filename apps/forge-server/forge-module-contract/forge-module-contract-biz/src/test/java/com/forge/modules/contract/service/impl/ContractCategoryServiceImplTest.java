package com.forge.modules.contract.service.impl;

import com.forge.common.exception.BusinessException;
import com.forge.modules.contract.dto.CategoryDtos;
import com.forge.modules.contract.entity.CtCategory;
import com.forge.modules.contract.entity.CtTemplate;
import com.forge.modules.contract.mapper.CtCategoryMapper;
import com.forge.modules.contract.mapper.CtTemplateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 合同分类服务单测：唯一编码守卫 / 删除守卫（子分类+模板挂载）/ 防环 / 含子孙收集
 *
 * @author forge-admin
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractCategoryServiceImplTest {

    @Mock
    private CtCategoryMapper categoryMapper;
    @Mock
    private CtTemplateMapper templateMapper;

    private ContractCategoryServiceImpl service;

    private ContractCategoryServiceImpl build() {
        if (service == null) {
            service = new ContractCategoryServiceImpl(categoryMapper, templateMapper);
        }
        return service;
    }

    @Test
    void addCategoryRejectsDuplicateCode() {
        lenient().when(categoryMapper.selectById(0L)).thenReturn(null);
        when(categoryMapper.insert(any(CtCategory.class))).thenThrow(new DuplicateKeyException("uk"));

        CategoryDtos.SaveRequest request = new CategoryDtos.SaveRequest();
        request.setCategoryName("劳动合同");
        request.setCategoryCode("LD");
        request.setParentId(0L);

        BusinessException ex = assertThrows(BusinessException.class, () -> build().addCategory(request));
        assertTrue(ex.getMessage().contains("已存在"));
    }

    @Test
    void deleteBlockedByChildCategory() {
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> build().deleteCategory(List.of(9L)));
        assertTrue(ex.getMessage().contains("子分类"));
    }

    @Test
    void deleteBlockedByTemplateMount() {
        // 第一次 count（子分类）=0，第二次（模板）=1
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(templateMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> build().deleteCategory(List.of(9L)));
        assertTrue(ex.getMessage().contains("挂载"));
    }

    @Test
    void updateRejectsMovingUnderOwnDescendant() {
        CtCategory self = node(5L, "销售合同", 2L);
        when(categoryMapper.selectById(5L)).thenReturn(self);
        when(categoryMapper.selectById(6L)).thenReturn(node(6L, "子级", 5L));
        // 全表（collectDescendantIds 用）：5 的子 6 → 6 的子 7
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                node(1L, "人事类", 0L), node(2L, "商务类", 0L),
                node(5L, "销售合同", 2L), node(6L, "子级", 5L), node(7L, "孙级", 6L)));

        CategoryDtos.SaveRequest request = new CategoryDtos.SaveRequest();
        request.setId(5L);
        request.setCategoryName("销售合同");
        request.setCategoryCode("XS");
        request.setParentId(6L); // 挂到自己的子孙 → 防环拒绝

        BusinessException ex = assertThrows(BusinessException.class,
                () -> build().updateCategory(request));
        assertTrue(ex.getMessage().contains("自身或其子分类"));
    }

    @Test
    void collectDescendantIdsIncludesAllLevels() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                node(1L, "人事类", 0L),
                node(4L, "劳动合同", 1L), node(5L, "保密协议", 1L),
                node(6L, "专版", 4L), node(7L, "孙版", 6L),
                node(2L, "商务类", 0L), node(8L, "采购合同", 2L)));

        Set<Long> ids = build().collectDescendantIds(1L);
        assertEquals(Set.of(1L, 4L, 5L, 6L, 7L), ids, "选父含全部子孙");
    }

    private CtCategory node(Long id, String name, Long parentId) {
        CtCategory c = new CtCategory();
        c.setId(id);
        c.setCategoryName(name);
        c.setCategoryCode("C" + id);
        c.setParentId(parentId);
        c.setStatus(CtCategory.STATUS_ENABLED);
        return c;
    }
}
