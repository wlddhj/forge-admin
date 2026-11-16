package com.forge.modules.contract.service.impl;

import com.forge.common.exception.BusinessException;
import com.forge.modules.contract.dto.ContractDtos;
import com.forge.modules.contract.entity.CtTemplate;
import com.forge.modules.contract.mapper.CtCategoryMapper;
import com.forge.modules.contract.mapper.CtContractMapper;
import com.forge.modules.contract.mapper.CtTemplateMapper;
import com.forge.modules.contract.mapper.CtTemplateVersionMapper;
import com.forge.modules.contract.listener.ContractModelProvisioner;
import com.forge.modules.contract.framework.pdf.PdfRenderClient;
import com.forge.modules.contract.framework.pdf.WatermarkStamper;
import com.forge.modules.contract.service.ContractNoGenerator;
import com.forge.modules.system.service.SysAttachmentService;
import com.forge.modules.system.service.SysTenantService;
import com.forge.modules.workflow.service.WfProcessInstanceService;
import com.forge.modules.contract.config.ContractProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 合同手填编号单测：租户内查重（留空跳过校验）
 *
 * @author forge-admin
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractManualNoTest {

    @Mock
    private CtContractMapper contractMapper;
    @Mock
    private CtTemplateMapper templateMapper;
    @Mock
    private CtCategoryMapper categoryMapper;
    @Mock
    private CtTemplateVersionMapper versionMapper;
    @Mock
    private PdfRenderClient pdfRenderClient;
    @Mock
    private WatermarkStamper watermarkStamper;
    @Mock
    private ContractNoGenerator contractNoGenerator;
    @Mock
    private SysAttachmentService attachmentService;
    @Mock
    private SysTenantService tenantService;
    @Mock
    private WfProcessInstanceService processInstanceService;
    @Mock
    private ContractModelProvisioner modelProvisioner;

    @Mock
    private com.forge.modules.contract.service.ContractCategoryService categoryService;

    private ContractServiceImpl build() {
        return new ContractServiceImpl(contractMapper, templateMapper, categoryMapper,
                categoryService, versionMapper, pdfRenderClient, watermarkStamper, contractNoGenerator,
                attachmentService, tenantService, processInstanceService, modelProvisioner,
                new ContractProperties(), new ObjectMapper());
    }

    private ContractDtos.CreateRequest request(String no) {
        ContractDtos.CreateRequest r = new ContractDtos.CreateRequest();
        r.setTemplateId(1L);
        r.setTitle("测试合同");
        r.setContractNo(no);
        return r;
    }

    private CtTemplate enabledTemplate() {
        CtTemplate t = new CtTemplate();
        t.setId(1L);
        t.setStatus(CtTemplate.STATUS_ENABLED);
        t.setCurrentVersionId(9L);
        return t;
    }

    @Test
    void createRejectsDuplicateManualNo() {
        lenient().when(templateMapper.selectById(1L)).thenReturn(enabledTemplate());
        when(contractMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> build().createContract(request("CT-LD-20261116-0001")));
        assertTrue(ex.getMessage().contains("已存在"));
    }

    @Test
    void createAllowsBlankNo() {
        lenient().when(templateMapper.selectById(1L)).thenReturn(enabledTemplate());
        lenient().when(contractMapper.selectCount(any())).thenReturn(0L);
        lenient().when(contractMapper.insert(any(com.forge.modules.contract.entity.CtContract.class))).thenReturn(1);

        // 留空不查重、不落编号，不抛异常即过
        build().createContract(request(" "));
    }
}
