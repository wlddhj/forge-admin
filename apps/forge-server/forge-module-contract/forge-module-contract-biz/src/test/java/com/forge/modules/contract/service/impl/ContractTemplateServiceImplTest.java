package com.forge.modules.contract.service.impl;

import com.forge.common.exception.BusinessException;
import com.forge.modules.contract.dto.FieldDef;
import com.forge.modules.contract.dto.TemplateDtos;
import com.forge.modules.contract.entity.CtTemplate;
import com.forge.modules.contract.entity.CtTemplateVersion;
import com.forge.modules.contract.mapper.CtContractMapper;
import com.forge.modules.contract.mapper.CtTemplateMapper;
import com.forge.modules.contract.mapper.CtTemplateVersionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 模板服务守卫单测：版本保存完整性校验 / 删除与字段定义修改守卫 / 版本号自增
 *
 * @author forge-admin
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractTemplateServiceImplTest {

    @Mock
    private CtTemplateMapper templateMapper;
    @Mock
    private CtTemplateVersionMapper versionMapper;
    @Mock
    private CtContractMapper contractMapper;
    @Mock
    private com.forge.modules.contract.mapper.CtCategoryMapper categoryMapper;
    @Mock
    private com.forge.modules.contract.service.ContractCategoryService categoryService;

    private ContractTemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ContractTemplateServiceImpl(templateMapper, versionMapper, contractMapper,
                categoryMapper, categoryService, new ObjectMapper());
    }

    @Test
    void saveVersionRejectsFieldDefMismatch() {
        CtTemplate template = new CtTemplate();
        template.setId(1L);
        template.setStatus(CtTemplate.STATUS_DRAFT);
        when(templateMapper.selectById(1L)).thenReturn(template);
        when(versionMapper.selectList(any())).thenReturn(List.of());

        TemplateDtos.VersionSaveRequest request = new TemplateDtos.VersionSaveRequest();
        request.setTemplateId(1L);
        request.setContentHtml("<p><span data-field=\"a\">甲</span> <span data-field=\"b\">乙</span></p>");
        request.setFieldDefs(List.of(def("a"), def("c")));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.saveVersion(request));
        assertTrue(ex.getMessage().contains("不一致"));
    }

    @Test
    void saveVersionIncrementsAndPublishesCurrent() {
        CtTemplate template = new CtTemplate();
        template.setId(1L);
        when(templateMapper.selectById(1L)).thenReturn(template);
        CtTemplateVersion v1 = new CtTemplateVersion();
        v1.setVersionNo(3);
        when(versionMapper.selectList(any())).thenReturn(List.of(v1));
        when(versionMapper.insert(any(CtTemplateVersion.class))).thenAnswer(inv -> {
            inv.getArgument(0, CtTemplateVersion.class).setId(99L);
            return 1;
        });

        TemplateDtos.VersionSaveRequest request = new TemplateDtos.VersionSaveRequest();
        request.setTemplateId(1L);
        request.setContentHtml("<p><span data-field=\"a\">甲</span></p>");
        request.setFieldDefs(List.of(def("a")));

        Long versionId = service.saveVersion(request);
        assertEquals(99L, versionId);
        // 断言存库实体：版本号自增 + 字段定义落 JSON
        ArgumentCaptor<CtTemplateVersion> captor = ArgumentCaptor.forClass(CtTemplateVersion.class);
        Mockito.verify(versionMapper).insert(captor.capture());
        assertEquals(4, captor.getValue().getVersionNo());
        assertTrue(captor.getValue().getFieldDefsJson().contains("\"key\":\"a\""));
        Mockito.verify(templateMapper).updateById(any(CtTemplate.class));
    }

    @Test
    void deleteTemplateBlockedWhenContractReferenced() {
        when(templateMapper.selectById(1L)).thenReturn(new CtTemplate());
        when(contractMapper.selectCount(any())).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteTemplate(1L));
        assertTrue(ex.getMessage().contains("禁止删除"));
    }

    @Test
    void updateFieldDefsBlockedWhenVersionReferenced() {
        CtTemplateVersion v = new CtTemplateVersion();
        v.setId(5L);
        v.setContentHtml("<p><span data-field=\"a\">甲</span></p>");
        when(versionMapper.selectById(5L)).thenReturn(v);
        when(contractMapper.selectCount(any())).thenReturn(1L);

        TemplateDtos.FieldDefsUpdateRequest request = new TemplateDtos.FieldDefsUpdateRequest();
        request.setVersionId(5L);
        request.setFieldDefs(List.of(def("a")));
        assertThrows(BusinessException.class, () -> service.updateFieldDefs(request));
    }

    @Test
    void changeStatusRequiresSavedContent() {
        CtTemplate t = new CtTemplate();
        t.setId(1L);
        t.setStatus(CtTemplate.STATUS_DRAFT);
        t.setCurrentVersionId(null);
        when(templateMapper.selectById(1L)).thenReturn(t);

        TemplateDtos.StatusRequest request = new TemplateDtos.StatusRequest();
        request.setId(1L);
        request.setStatus(CtTemplate.STATUS_ENABLED);
        assertThrows(BusinessException.class, () -> service.changeStatus(1L, CtTemplate.STATUS_ENABLED));
    }

    @Test
    void createTemplateRejectsNonLeafCategory() {
        // 分类存在但有子节点 → 禁止挂载
        com.forge.modules.contract.entity.CtCategory category =
                new com.forge.modules.contract.entity.CtCategory();
        category.setId(2L);
        category.setCategoryName("商务类");
        category.setStatus(com.forge.modules.contract.entity.CtCategory.STATUS_ENABLED);
        lenient().when(categoryMapper.selectById(2L)).thenReturn(category);
        lenient().when(categoryMapper.selectCount(any())).thenReturn(2L);

        TemplateDtos.CreateRequest request = new TemplateDtos.CreateRequest();
        request.setCategoryId(2L);
        request.setName("采购模板");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createTemplate(request));
        assertTrue(ex.getMessage().contains("叶子"));
    }

    @Test
    void createTemplateRejectsDisabledCategory() {
        com.forge.modules.contract.entity.CtCategory category =
                new com.forge.modules.contract.entity.CtCategory();
        category.setId(9L);
        category.setCategoryName("停用分类");
        category.setStatus(com.forge.modules.contract.entity.CtCategory.STATUS_DISABLED);
        lenient().when(categoryMapper.selectById(9L)).thenReturn(category);
        lenient().when(categoryMapper.selectCount(any())).thenReturn(0L);

        TemplateDtos.CreateRequest request = new TemplateDtos.CreateRequest();
        request.setCategoryId(9L);
        request.setName("模板");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createTemplate(request));
        assertTrue(ex.getMessage().contains("停用"));
    }

    private FieldDef def(String key) {
        FieldDef def = new FieldDef();
        def.setKey(key);
        def.setLabel(key + "名");
        def.setType(FieldDef.TYPE_TEXT);
        def.setRequired(Boolean.TRUE);
        return def;
    }
}
