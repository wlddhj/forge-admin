package com.forge.modules.contract.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.common.exception.BusinessException;
import com.forge.common.utils.SecurityUtils;
import com.forge.framework.tenant.core.context.TenantContextHolder;
import com.forge.modules.contract.config.ContractProperties;
import com.forge.modules.contract.dto.ContractDtos;
import com.forge.modules.contract.dto.FieldDef;
import com.forge.modules.workflow.dto.instance.ProcessInstanceResponse;
import com.forge.modules.workflow.dto.instance.ProcessStartRequest;
import com.forge.modules.contract.entity.CtCategory;
import com.forge.modules.contract.entity.CtContract;
import com.forge.modules.contract.entity.CtTemplate;
import com.forge.modules.contract.entity.CtTemplateVersion;
import com.forge.modules.contract.enums.PaperSize;
import com.forge.modules.contract.framework.html.ContractHtmlProcessor;
import com.forge.modules.contract.framework.pdf.InMemoryMultipartFile;
import com.forge.modules.contract.framework.pdf.PdfRenderClient;
import com.forge.modules.contract.framework.pdf.WatermarkStamper;
import com.forge.modules.contract.mapper.CtCategoryMapper;
import com.forge.modules.contract.mapper.CtContractMapper;
import com.forge.modules.contract.mapper.CtTemplateMapper;
import com.forge.modules.contract.listener.ContractModelProvisioner;
import com.forge.modules.contract.mapper.CtTemplateVersionMapper;
import com.forge.modules.contract.service.ContractCategoryService;
import com.forge.modules.contract.service.ContractNoGenerator;
import com.forge.modules.contract.service.ContractService;
import com.forge.modules.system.dto.attachment.AttachmentResponse;
import com.forge.modules.system.entity.SysAttachment;
import com.forge.modules.system.entity.SysTenant;
import com.forge.modules.system.service.SysAttachmentService;
import com.forge.modules.system.service.SysTenantService;
import com.forge.modules.workflow.service.WfProcessInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 合同服务实现
 *
 * @author forge-admin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {

    private static final String BUSINESS_KEY_PREFIX = "contract:";
    private static final String APPLICATION_PDF = "application/pdf";

    private final CtContractMapper contractMapper;
    private final CtTemplateMapper templateMapper;
    private final CtCategoryMapper categoryMapper;
    private final ContractCategoryService categoryService;
    private final CtTemplateVersionMapper versionMapper;
    private final PdfRenderClient pdfRenderClient;
    private final WatermarkStamper watermarkStamper;
    private final ContractNoGenerator contractNoGenerator;
    private final SysAttachmentService attachmentService;
    private final SysTenantService tenantService;
    private final WfProcessInstanceService processInstanceService;
    private final ContractModelProvisioner modelProvisioner;
    private final ContractProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public Page<ContractDtos.ContractResponse> pageContracts(int pageNum, int pageSize, String title,
                                                             String contractNo, Long categoryId, Integer status) {
        List<Long> templateIds = null;
        if (categoryId != null) {
            // 选父分类含全部子孙：分类集 → 模板集
            List<Long> categoryIds = List.copyOf(categoryService.collectDescendantIds(categoryId));
            templateIds = categoryIds.isEmpty() ? List.of()
                    : templateMapper.selectList(new LambdaQueryWrapper<CtTemplate>()
                            .in(CtTemplate::getCategoryId, categoryIds))
                    .stream().map(CtTemplate::getId).toList();
            if (templateIds.isEmpty()) {
                Page<ContractDtos.ContractResponse> empty = new Page<>(pageNum, pageSize, 0);
                empty.setRecords(List.of());
                return empty;
            }
        }
        Page<CtContract> page = contractMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<CtContract>()
                        .like(notBlank(title), CtContract::getTitle, title)
                        .like(notBlank(contractNo), CtContract::getContractNo, contractNo)
                        .in(templateIds != null, CtContract::getTemplateId, templateIds)
                        .eq(status != null, CtContract::getStatus, status)
                        .orderByDesc(CtContract::getUpdateTime));
        Page<ContractDtos.ContractResponse> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<ContractDtos.ContractResponse> records =
                page.getRecords().stream().map(ContractDtos.ContractResponse::from).toList();
        batchFillTemplateInfo(records);
        result.setRecords(records);
        return result;
    }

    @Override
    public ContractDtos.ContractResponse getContract(Long id) {
        ContractDtos.ContractResponse resp = ContractDtos.ContractResponse.from(requireContract(id));
        batchFillTemplateInfo(List.of(resp));
        return resp;
    }

    @Override
    public Long createContract(ContractDtos.CreateRequest request) {
        CtTemplate template = templateMapper.selectById(request.getTemplateId());
        if (template == null) {
            throw new BusinessException(404, "模板不存在");
        }
        if (template.getStatus() != CtTemplate.STATUS_ENABLED || template.getCurrentVersionId() == null) {
            throw new BusinessException(400, "模板未启用，无法创建合同");
        }
        validateContractNo(request.getContractNo(), null);
        CtContract c = new CtContract();
        c.setContractNo(normalizeNo(request.getContractNo()));
        c.setTitle(request.getTitle().trim());
        c.setTemplateId(template.getId());
        c.setTemplateVersionId(template.getCurrentVersionId());
        c.setFieldValues(request.getFieldValues());
        c.setStatus(CtContract.STATUS_DRAFT);
        c.setCreateBy(SecurityUtils.getCurrentUserId());
        contractMapper.insert(c);
        return c.getId();
    }

    @Override
    public void updateContract(ContractDtos.UpdateRequest request) {
        CtContract exists = requireEditable(request.getId());
        // CAS：仅 0/3/4 可改，防与提交并发（提交置 1 后编辑覆写会污染在途合同）
        validateContractNo(request.getContractNo(), exists.getId());
        int rows = contractMapper.update(null, new LambdaUpdateWrapper<CtContract>()
                .eq(CtContract::getId, exists.getId())
                .in(CtContract::getStatus, CtContract.STATUS_DRAFT,
                        CtContract.STATUS_REJECTED, CtContract.STATUS_CANCELED)
                .set(CtContract::getTitle, request.getTitle().trim())
                .set(CtContract::getContractNo, normalizeNo(request.getContractNo()))
                .set(CtContract::getFieldValues, request.getFieldValues())
                .set(CtContract::getStatus, CtContract.STATUS_DRAFT) // 3/4 重置回草稿语义
                .set(CtContract::getUpdateBy, SecurityUtils.getCurrentUserId()));
        if (rows == 0) {
            throw new BusinessException(409, "合同状态已变更，请刷新后重试");
        }
    }

    @Override
    public void deleteContract(Long id) {
        CtContract exists = requireEditable(id);
        // 条件删除：仅 0/3/4 可删，防与提交并发
        int rows = contractMapper.delete(new LambdaQueryWrapper<CtContract>()
                .eq(CtContract::getId, exists.getId())
                .in(CtContract::getStatus, CtContract.STATUS_DRAFT,
                        CtContract.STATUS_REJECTED, CtContract.STATUS_CANCELED));
        if (rows == 0) {
            throw new BusinessException(409, "合同状态已变更，请刷新后重试");
        }
    }

    @Override
    public byte[] previewPdf(Long id) {
        // 任意状态可预览：定稿态看归档件（详情直链），其余按锁定版本实时渲染
        CtContract contract = requireContract(id);
        CtTemplate template = requireTemplate(contract.getTemplateId());
        CtTemplateVersion version = requireVersion(contract.getTemplateVersionId());
        String filled = ContractHtmlProcessor.fillChips(version.getContentHtml(), contract.getFieldValues());
        return pdfRenderClient.render(filled, PaperSize.of(template.getPaperSize()),
                template.getCustomWidthMm(), template.getCustomHeightMm());
    }

    @Override
    public byte[] printPdf(Long id, ContractDtos.PrintRequest request) {
        CtContract contract = requireContract(id);
        CtTemplate template = requireTemplate(contract.getTemplateId());
        PaperSize paper = notBlank(request.getPaperSize()) ? PaperSize.of(request.getPaperSize())
                : PaperSize.of(template.getPaperSize());
        BigDecimal w = request.getCustomWidthMm() != null ? request.getCustomWidthMm() : template.getCustomWidthMm();
        BigDecimal h = request.getCustomHeightMm() != null ? request.getCustomHeightMm() : template.getCustomHeightMm();
        if (paper == PaperSize.CUSTOM && (w == null || h == null || w.signum() <= 0 || h.signum() <= 0)) {
            throw new BusinessException(400, "自定义纸张须提供正的宽高（mm）");
        }

        String content;
        String watermarkFlag;
        if (contract.getStatus() == CtContract.STATUS_APPROVED) {
            // 定稿用快照；快照缺失（历史数据）回退按锁定版本+字段值现场渲染
            if (contract.getRenderedHtml() != null && !contract.getRenderedHtml().isBlank()) {
                content = contract.getRenderedHtml();
            } else {
                CtTemplateVersion version = requireVersion(contract.getTemplateVersionId());
                content = ContractHtmlProcessor.fillChips(version.getContentHtml(), contract.getFieldValues());
            }
            watermarkFlag = "定稿";
        } else {
            CtTemplateVersion version = requireVersion(contract.getTemplateVersionId());
            content = ContractHtmlProcessor.fillChips(version.getContentHtml(), contract.getFieldValues());
            watermarkFlag = contract.getStatus() == CtContract.STATUS_APPROVING ? "审批中" : "草稿";
        }
        byte[] pdf = pdfRenderClient.render(content, paper, w, h);
        return watermarkStamper.stamp(pdf, watermarkTenantName() + " · " + watermarkFlag);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitContract(Long id) {
        CtContract contract = requireEditable(id);
        CtTemplateVersion version = requireVersion(contract.getTemplateVersionId());
        validateRequiredFields(version, contract.getFieldValues());

        // 当前租户无定义时懒部署默认模型（定义表租户隔离，启动预置仅覆盖默认租户）
        modelProvisioner.ensureModelExists();
        String definitionId = modelProvisioner.resolveDefinitionId();
        String businessKey = BUSINESS_KEY_PREFIX + contract.getId();
        ProcessStartRequest startRequest = new ProcessStartRequest();
        startRequest.setProcessDefinitionId(definitionId);
        startRequest.setBusinessKey(businessKey);
        startRequest.setComment("提交合同审批");
        startRequest.setTitle(contract.getTitle());
        Map<String, Object> variables = new HashMap<>();
        variables.put("contractId", contract.getId());
        variables.put("contractTitle", contract.getTitle());
        startRequest.setVariables(variables);
        processInstanceService.startProcess(startRequest);

        ProcessInstanceResponse activeInstance = processInstanceService
                .getInstancesByBusinessKey(businessKey).stream()
                .filter(i -> i.getEndTime() == null)
                .findFirst()
                .orElseThrow(() -> new BusinessException(500, "流程已发起但未查到在途实例"));

        // CAS：仅 0/3/4 可置为审批中，防双击重复提交
        int rows = contractMapper.update(null, new LambdaUpdateWrapper<CtContract>()
                .eq(CtContract::getId, contract.getId())
                .in(CtContract::getStatus, CtContract.STATUS_DRAFT,
                        CtContract.STATUS_REJECTED, CtContract.STATUS_CANCELED)
                .set(CtContract::getStatus, CtContract.STATUS_APPROVING)
                .set(CtContract::getProcessInstanceId, Long.parseLong(activeInstance.getId()))
                .set(CtContract::getProcessNo, activeInstance.getProcessNo())
                .set(CtContract::getUpdateBy, SecurityUtils.getCurrentUserId()));
        if (rows == 0) {
            throw new BusinessException(409, "合同状态已变更，请刷新后重试");
        }
        log.info("合同已提交审批: contractId={}, businessKey={}, processNo={}",
                contract.getId(), businessKey, activeInstance.getProcessNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finalizeApproved(CtContract contract) {
        CtTemplate template = requireTemplate(contract.getTemplateId());
        CtTemplateVersion version = requireVersion(contract.getTemplateVersionId());

        // 定稿快照：模板内容 + 锁定字段值（此后模板/字段改动不影响本合同）
        String renderedHtml = ContractHtmlProcessor.fillChips(version.getContentHtml(), contract.getFieldValues());
        byte[] pdf = pdfRenderClient.render(renderedHtml, PaperSize.of(template.getPaperSize()),
                template.getCustomWidthMm(), template.getCustomHeightMm());
        byte[] watermarked = watermarkStamper.stamp(pdf, watermarkTenantName() + " · 定稿");

        String fileName = "contract-" + contract.getId() + ".pdf";
        AttachmentResponse attachment = attachmentService.upload(
                new InMemoryMultipartFile("file", fileName, APPLICATION_PDF, watermarked),
                "contract", contract.getId());
        CtCategory category = template.getCategoryId() == null ? null
                : categoryMapper.selectById(template.getCategoryId());
        if (category == null) {
            throw new BusinessException(400, "模板挂载的合同分类已不存在，无法生成编号");
        }
        // 手填编号沿用；留空才自动生成
        String contractNo = contract.getContractNo() != null && !contract.getContractNo().isBlank()
                ? contract.getContractNo()
                : contractNoGenerator.next(category.getCategoryCode());

        // CAS：仅审批中可落终态 2；未命中=已定稿或状态漂移，幂等跳过
        int rows = contractMapper.update(null, new LambdaUpdateWrapper<CtContract>()
                .eq(CtContract::getId, contract.getId())
                .eq(CtContract::getStatus, CtContract.STATUS_APPROVING)
                .set(CtContract::getStatus, CtContract.STATUS_APPROVED)
                .set(CtContract::getRenderedHtml, renderedHtml)
                .set(CtContract::getPdfAttachmentId, attachment.getId())
                .set(CtContract::getContractNo, contractNo));
        if (rows == 0) {
            log.info("合同定稿 CAS 未命中，跳过（幂等）: contractId={}", contract.getId());
            return;
        }
        log.info("合同定稿归档: contractId={}, contractNo={}, attachmentId={}",
                contract.getId(), contractNo, attachment.getId());
    }

    // ---------- 私有工具 ----------

    /** 批量装配模板名/分类/版本号与定稿直链（避免列表页 N+1 单查） */
    private void batchFillTemplateInfo(List<ContractDtos.ContractResponse> records) {
        if (records.isEmpty()) {
            return;
        }
        List<Long> templateIds = records.stream().map(ContractDtos.ContractResponse::getTemplateId).distinct().toList();
        List<Long> versionIds = records.stream().map(ContractDtos.ContractResponse::getTemplateVersionId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, CtTemplate> templateMap = templateIds.isEmpty() ? Map.of()
                : templateMapper.selectBatchIds(templateIds).stream()
                        .collect(Collectors.toMap(CtTemplate::getId, t -> t));
        Map<Long, CtTemplateVersion> versionMap = versionIds.isEmpty() ? Map.of()
                : versionMapper.selectBatchIds(versionIds).stream()
                        .collect(Collectors.toMap(CtTemplateVersion::getId, v -> v));
        List<Long> categoryIds = templateMap.values().stream().map(CtTemplate::getCategoryId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, CtCategory> categoryMap = categoryIds.isEmpty() ? Map.of()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(Collectors.toMap(CtCategory::getId, c -> c));
        for (ContractDtos.ContractResponse resp : records) {
            CtTemplate t = templateMap.get(resp.getTemplateId());
            if (t != null) {
                resp.setTemplateName(t.getName());
                CtCategory c = categoryMap.get(t.getCategoryId());
                if (c != null) {
                    resp.setCategoryName(c.getCategoryName());
                    resp.setCategoryCode(c.getCategoryCode());
                }
            }
            CtTemplateVersion v = versionMap.get(resp.getTemplateVersionId());
            if (v != null) {
                resp.setTemplateVersionNo(v.getVersionNo());
            }
            if (resp.getPdfAttachmentId() != null) {
                SysAttachment attachment = attachmentService.getById(resp.getPdfAttachmentId());
                if (attachment != null) {
                    resp.setPdfFileUrl(attachment.getFileUrl());
                }
            }
        }
    }

    private void validateRequiredFields(CtTemplateVersion version, Map<String, Object> values) {
        List<FieldDef> defs = parseFieldDefs(version.getFieldDefsJson());
        Map<String, Object> safeValues = values == null ? Map.of() : values;
        for (FieldDef def : defs) {
            Object value = safeValues.get(def.getKey());
            boolean missing = value == null || String.valueOf(value).isBlank();
            if (Boolean.TRUE.equals(def.getRequired()) && missing) {
                throw new BusinessException(400, "必填变量未填写：" + safeLabel(def));
            }
            if (!missing && FieldDef.TYPE_SELECT.equals(def.getType())
                    && def.getOptions() != null && !def.getOptions().isEmpty()
                    && !def.getOptions().contains(String.valueOf(value))) {
                throw new BusinessException(400, "变量「" + safeLabel(def) + "」的值不在可选项内");
            }
        }
    }

    private String safeLabel(FieldDef def) {
        return def.getLabel() == null ? def.getKey() : def.getLabel();
    }

    private List<FieldDef> parseFieldDefs(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return objectMapper.readValue(json, new TypeReference<List<FieldDef>>() {
        });
    }

    /** 水印租户名（租户上下文缺失时回退「合同」） */
    private String watermarkTenantName() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            SysTenant tenant = tenantService.getById(tenantId);
            if (tenant != null && tenant.getName() != null && !tenant.getName().isBlank()) {
                return tenant.getName();
            }
        }
        return "合同";
    }

    /** 手填编号：trim；与租户内其他合同查重（自动生成的唯一性由 Redis 序列保证） */
    private void validateContractNo(String contractNo, Long selfId) {
        if (contractNo == null || contractNo.isBlank()) {
            return;
        }
        String no = normalizeNo(contractNo);
        Long dup = contractMapper.selectCount(new LambdaQueryWrapper<CtContract>()
                .eq(CtContract::getContractNo, no)
                .ne(selfId != null, CtContract::getId, selfId)
                .last("LIMIT 1"));
        if (dup != null && dup > 0) {
            throw new BusinessException(400, "合同编号「" + no + "」已存在");
        }
    }

    private String normalizeNo(String contractNo) {
        return contractNo == null || contractNo.isBlank() ? null : contractNo.trim();
    }

    private CtContract requireContract(Long id) {
        CtContract c = contractMapper.selectById(id);
        if (c == null) {
            throw new BusinessException(404, "合同不存在");
        }
        return c;
    }

    /** 可编辑态校验：0 草稿 / 3 驳回 / 4 撤销 */
    private CtContract requireEditable(Long id) {
        return requireEditable(id, "仅草稿/驳回/撤销状态可编辑");
    }

    private CtContract requireEditable(Long id, String message) {
        CtContract c = requireContract(id);
        if (c.getStatus() == CtContract.STATUS_APPROVING || c.getStatus() == CtContract.STATUS_APPROVED) {
            throw new BusinessException(400, message);
        }
        return c;
    }

    private CtTemplate requireTemplate(Long id) {
        CtTemplate t = templateMapper.selectById(id);
        if (t == null) {
            throw new BusinessException(404, "模板不存在");
        }
        return t;
    }

    private CtTemplateVersion requireVersion(Long id) {
        CtTemplateVersion v = versionMapper.selectById(id);
        if (v == null) {
            throw new BusinessException(404, "模板版本不存在");
        }
        return v;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
