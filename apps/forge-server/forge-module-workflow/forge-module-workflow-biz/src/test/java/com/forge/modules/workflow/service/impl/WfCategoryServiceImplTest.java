package com.forge.modules.workflow.service.impl;

import com.forge.common.exception.BusinessException;
import com.forge.modules.workflow.dto.category.CategoryRequest;
import com.forge.modules.workflow.entity.WfCategory;
import com.forge.modules.workflow.mapper.WfCategoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

/**
 * 流程分类单元测试
 *
 * 覆盖：子孙集合纯函数、防环、编码自动生成（字符集/同级避让/容量上限）、
 * 移动父分类的级联前缀替换、未移动编码不变、入参 code 覆盖保护
 */
class WfCategoryServiceImplTest {

    private static final Pattern SEGMENT_PATTERN = Pattern.compile("^[0-9A-Z]{2}$");

    private WfCategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = Mockito.spy(new WfCategoryServiceImpl(Mockito.mock(WfCategoryMapper.class)));
    }

    private WfCategory category(Long id, Long parentId) {
        return category(id, parentId, "CAT_" + id);
    }

    private WfCategory category(Long id, Long parentId, String code) {
        WfCategory category = new WfCategory();
        category.setId(id);
        category.setParentId(parentId);
        category.setCategoryName("分类" + id);
        category.setCategoryCode(code);
        return category;
    }

    /** 固定序列随机源：按预设值循环返回，测试确定化 */
    static class FixedRandom extends Random {
        private final int[] values;
        private int idx;

        FixedRandom(int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(int bound) {
            return values[idx++ % values.length] % bound;
        }
    }

    // ==================== 子孙集合与防环（wf-category-tree 回归） ====================

    @Test
    @DisplayName("子孙集合：三层链路全量命中，叶子与无关节点不命中")
    void collectDescendantIdsShouldWalkHierarchy() {
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
        request.setParentId(3L);

        assertThrows(BusinessException.class, () -> service.updateCategory(request));
        verify(service, Mockito.never()).updateById(any(WfCategory.class));
    }

    @Test
    @DisplayName("编辑：父级不存在应抛业务异常")
    void updateCategoryShouldRejectMissingParent() {
        WfCategory existing = category(3L, 0L, "A3");
        doReturn(existing).when(service).getById(3L);
        doReturn(List.of(existing)).when(service).list();

        CategoryRequest request = new CategoryRequest();
        request.setId(3L);
        request.setCategoryName("分类3");
        request.setParentId(999L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateCategory(request));
        assertTrue(ex.getMessage().contains("父分类不存在"));
    }

    @Test
    @DisplayName("编辑：父级为顶级时放行（移动到顶级），parentId 缺省归一为 0")
    void updateCategoryShouldAllowLegalParent() {
        WfCategory existing = category(3L, 2L);
        doReturn(existing).when(service).getById(3L);
        doReturn(List.of(category(1L, 0L), category(2L, 1L), existing)).when(service).list();
        doReturn(true).when(service).updateById(any(WfCategory.class));
        doReturn(true).when(service).updateBatchById(anyCollection());

        CategoryRequest topRequest = new CategoryRequest();
        topRequest.setId(3L);
        topRequest.setCategoryName("分类3");
        topRequest.setParentId(0L);
        service.updateCategory(topRequest);

        CategoryRequest nullParentRequest = new CategoryRequest();
        nullParentRequest.setId(3L);
        nullParentRequest.setCategoryName("分类3");
        service.updateCategory(nullParentRequest);

        verify(service, Mockito.times(2)).updateById(any(WfCategory.class));
        assertEquals(0L, existing.getParentId());
        // 移动到顶级后 code = 新随机 2 位段
        assertTrue(SEGMENT_PATTERN.matcher(existing.getCategoryCode()).matches(),
                "code 应为 2 位随机段，实际: " + existing.getCategoryCode());
    }

    // ==================== 编码自动生成 ====================

    @Test
    @DisplayName("段生成：字符集合法（数字+大写字母 2 位）")
    void generateSegmentShouldUseLegalCharset() {
        String segment = WfCategoryServiceImpl.generateSegment(Set.of(), new FixedRandom(5, 20));
        assertTrue(SEGMENT_PATTERN.matcher(segment).matches());
        assertEquals("5K", segment);
    }

    @Test
    @DisplayName("段生成：避开同级已占用段")
    void generateSegmentShouldAvoidUsed() {
        // 段序列：先取 (0,0)="00" 已占用被拒；随后 (0,1)="01" 命中
        String segment = WfCategoryServiceImpl.generateSegment(Set.of("00"), new FixedRandom(0, 0, 0, 1));
        assertEquals("01", segment);
    }

    @Test
    @DisplayName("段生成：同级段耗尽重试超限抛业务异常")
    void generateSegmentShouldFailWhenExhausted() {
        Set<String> all = new java.util.HashSet<>();
        for (char a : WfCategoryServiceImpl.CODE_CHARS.toCharArray()) {
            for (char b : WfCategoryServiceImpl.CODE_CHARS.toCharArray()) {
                all.add("" + a + b);
            }
        }
        assertThrows(BusinessException.class,
                () -> WfCategoryServiceImpl.generateSegment(all, new FixedRandom(0, 1, 2)));
    }

    @Test
    @DisplayName("同级已用段：取 parentId 相同分类的 code 末两位")
    void usedSiblingSegmentsShouldCollectTailSegments() {
        List<WfCategory> all = List.of(
                category(1L, 0L, "A1"), category(2L, 0L, "B2"),
                category(3L, 1L, "A1C3"), category(4L, 1L, "A1D4"), category(5L, null, "E5")
        );

        assertEquals(Set.of("A1", "B2"), WfCategoryServiceImpl.usedSiblingSegments(all, 0L));
        assertEquals(Set.of("C3", "D4"), WfCategoryServiceImpl.usedSiblingSegments(all, 1L));
        assertEquals(Set.of(), WfCategoryServiceImpl.usedSiblingSegments(all, 99L));
    }

    @Test
    @DisplayName("新增：编码=父编码+2位随机段，避开同级已用段，忽略入参 code")
    void addCategoryShouldGenerateHierarchicalCode() {
        List<WfCategory> all = List.of(
                category(1L, 0L, "A1"), category(2L, 1L, "A1C3"), category(3L, 1L, "A1D4")
        );
        doReturn(all).when(service).list();
        doReturn(true).when(service).save(any(WfCategory.class));

        CategoryRequest request = new CategoryRequest();
        request.setCategoryName("新子分类");
        request.setParentId(1L);
        request.setCategoryCode("HANDWRITTEN");

        service.addCategory(request);

        ArgumentCaptor<WfCategory> captor = ArgumentCaptor.forClass(WfCategory.class);
        verify(service).save(captor.capture());
        WfCategory saved = captor.getValue();
        assertEquals(1L, saved.getParentId());
        assertNotEquals("HANDWRITTEN", saved.getCategoryCode());
        assertTrue(saved.getCategoryCode().startsWith("A1") && saved.getCategoryCode().length() == 4,
                "应为父编码+2位段，实际: " + saved.getCategoryCode());
        assertNotEquals("A1C3", saved.getCategoryCode());
        assertNotEquals("A1D4", saved.getCategoryCode());
    }

    // ==================== 移动级联替换 ====================

    @Test
    @DisplayName("移动父分类：自身重新生成段，子孙编码前缀级联替换")
    void updateCategoryShouldCascadeRewriteOnMove() {
        WfCategory root = category(1L, 0L, "A1");
        WfCategory child = category(2L, 1L, "A1B2");
        WfCategory grandChild = category(3L, 2L, "A1B2C3");
        WfCategory newParent = category(9L, 0L, "Z9");
        doReturn(root).when(service).getById(1L);
        doReturn(List.of(root, child, grandChild, newParent)).when(service).list();
        doReturn(true).when(service).updateById(any(WfCategory.class));
        doReturn(true).when(service).updateBatchById(anyCollection());

        CategoryRequest request = new CategoryRequest();
        request.setId(1L);
        request.setCategoryName("分类1");
        request.setParentId(9L);
        request.setCategoryCode("HANDWRITTEN");

        service.updateCategory(request);

        // 自身：新父编码 Z9 + 2 位新段；入参 code 不生效
        assertTrue(root.getCategoryCode().matches("^Z9[0-9A-Z]{2}$"),
                "自身应为 Z9+新段，实际: " + root.getCategoryCode());
        assertEquals(9L, root.getParentId());

        // 子孙：新前缀 + 原后缀
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<WfCategory>> batchCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(service).updateBatchById(batchCaptor.capture());
        List<WfCategory> updated = new ArrayList<>(batchCaptor.getValue());
        assertEquals(2, updated.size());
        String newRootCode = root.getCategoryCode();
        assertTrue(updated.stream().anyMatch(c -> c.getId().equals(2L)
                && c.getCategoryCode().equals(newRootCode + "B2")));
        assertTrue(updated.stream().anyMatch(c -> c.getId().equals(3L)
                && c.getCategoryCode().equals(newRootCode + "B2C3")));
    }

    @Test
    @DisplayName("移动级联：子孙编码不符合前缀规则时明确报错而非产出错码")
    void updateCategoryShouldRejectBrokenHierarchyData() {
        WfCategory root = category(1L, 0L, "A1");
        WfCategory brokenChild = category(2L, 1L, "BROKEN");
        WfCategory newParent = category(9L, 0L, "Z9");
        doReturn(root).when(service).getById(1L);
        doReturn(List.of(root, brokenChild, newParent)).when(service).list();
        doReturn(true).when(service).updateById(any(WfCategory.class));

        CategoryRequest request = new CategoryRequest();
        request.setId(1L);
        request.setCategoryName("分类1");
        request.setParentId(9L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateCategory(request));
        assertTrue(ex.getMessage().contains("不符合层级规则"));
        // 自身也未落库
        verify(service, Mockito.never()).updateById(any(WfCategory.class));
    }

    @Test
    @DisplayName("未移动父分类：编码保持不变（入参携带 code 也忽略）")
    void updateCategoryShouldKeepCodeWhenParentUnchanged() {
        WfCategory existing = category(2L, 1L, "A1B2");
        doReturn(existing).when(service).getById(2L);
        doReturn(List.of(category(1L, 0L, "A1"), existing)).when(service).list();
        doReturn(true).when(service).updateById(any(WfCategory.class));

        CategoryRequest request = new CategoryRequest();
        request.setId(2L);
        request.setCategoryName("改名");
        request.setParentId(1L);
        request.setCategoryCode("HANDWRITTEN");

        service.updateCategory(request);

        assertEquals("A1B2", existing.getCategoryCode());
        assertEquals("改名", existing.getCategoryName());
        verify(service, Mockito.never()).updateBatchById(anyCollection());
    }
}
