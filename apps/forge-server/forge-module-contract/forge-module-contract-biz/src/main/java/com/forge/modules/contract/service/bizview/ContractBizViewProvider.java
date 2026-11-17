package com.forge.modules.contract.service.bizview;

import com.forge.modules.contract.dto.ContractDtos;
import com.forge.modules.contract.service.ContractService;
import com.forge.modules.system.constant.AttachmentBizTypes;
import com.forge.modules.system.service.DictLabelResolver;
import com.forge.modules.workflow.dto.bizview.WfBizViewAttachmentRef;
import com.forge.modules.workflow.dto.bizview.WfBizViewData;
import com.forge.modules.workflow.dto.bizview.WfBizViewField;
import com.forge.modules.workflow.spi.WfBizViewProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 合同业务单据视图提供方（businessKey 前缀 contract:）。
 * <p>
 * 审批抽屉展示合同管理字段 + 附件清单 + PDF 实时预览（previewPdf 水印三态复用）；
 * 模板变量（fieldValues）为无中文标签的模板 key，不在结构化字段中平铺，
 * 单据全文经 PDF 预览查看。
 *
 * @author forge-admin
 */
@Component
@RequiredArgsConstructor
public class ContractBizViewProvider implements WfBizViewProvider {

    /** businessKey 前缀（对齐 ContractServiceImpl.BUSINESS_KEY_PREFIX 的冒号前段） */
    public static final String BIZ_TYPE = "contract";

    /** 合同状态字典类型（码值对齐 ct_contract.status，种子见 upgrade-20261118-contract-status-dict.sql） */
    public static final String STATUS_DICT_TYPE = "contract_status";

    private final ContractService contractService;
    private final DictLabelResolver dictLabelResolver;

    @Override
    public String bizType() {
        return BIZ_TYPE;
    }

    @Override
    public WfBizViewData getBizView(String bizId) {
        Long id = parseBizId(bizId);
        if (id == null) {
            return null;
        }
        ContractDtos.ContractResponse contract = contractService.getContract(id);
        if (contract == null) {
            return null;
        }

        List<WfBizViewField> fields = new ArrayList<>();
        fields.add(new WfBizViewField("合同编号", contract.getContractNo()));
        fields.add(new WfBizViewField("所属分类", contract.getCategoryName()));
        fields.add(new WfBizViewField("合同模板", contract.getTemplateName()));
        fields.add(new WfBizViewField("合同状态", statusName(contract.getStatus())));
        fields.add(new WfBizViewField("备注", contract.getRemark()));

        WfBizViewData data = new WfBizViewData();
        data.setTitle(contract.getTitle());
        data.setFields(fields);
        data.setAttachmentRef(new WfBizViewAttachmentRef(AttachmentBizTypes.CONTRACT, id));
        data.setPreviewEnabled(true);
        return data;
    }

    @Override
    public byte[] getBizPreview(String bizId) {
        Long id = parseBizId(bizId);
        if (id == null) {
            return null;
        }
        return contractService.previewPdf(id);
    }

    private Long parseBizId(String bizId) {
        try {
            return Long.parseLong(bizId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 合同状态 label：统一走通用字典转换工具（contract_status 字典），
     * 未命中（字典未配置/码值越界）回退码值本身，不在业务侧硬编码映射
     */
    private String statusName(Integer status) {
        if (status == null) {
            return null;
        }
        return dictLabelResolver.resolveOrDefault(STATUS_DICT_TYPE, status, String.valueOf(status));
    }
}
