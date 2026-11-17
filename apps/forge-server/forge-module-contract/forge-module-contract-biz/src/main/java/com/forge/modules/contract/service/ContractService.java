package com.forge.modules.contract.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.contract.dto.ContractDtos;
import com.forge.modules.contract.entity.CtContract;

/**
 * 合同服务
 *
 * @author forge-admin
 */
public interface ContractService {

    /** 分页查询合同 */
    Page<ContractDtos.ContractResponse> pageContracts(int pageNum, int pageSize, String title,
                                                      String contractNo, Long categoryId, Integer status);

    /** 合同详情（含模板名/版本号） */
    ContractDtos.ContractResponse getContract(Long id);

    /** 新建草稿（锁定模板当前版本） */
    Long createContract(ContractDtos.CreateRequest request);

    /** 编辑草稿（status=3/4 时重置为 0 语义） */
    void updateContract(ContractDtos.UpdateRequest request);

    /** 修改备注（任意状态可改：备注为管理性信息，不参与渲染与定稿快照） */
    void updateRemark(Long id, String remark);

    /** 删除（仅草稿态） */
    void deleteContract(Long id);

    /** 实时预览 PDF（按已存合同渲染） */
    byte[] previewPdf(Long id);

    /**
     * 临时预览（编辑表单用）：按模板版本+表单当前值渲染，不落库不依赖合同存在（新建未保存亦可预览）。
     */
    byte[] previewDraft(Long templateVersionId, java.util.Map<String, Object> fieldValues);

    /** 打印 PDF（草稿盖「草稿」水印；已通过用定稿快照盖「定稿」水印；按所选纸张重渲染） */
    byte[] printPdf(Long id, ContractDtos.PrintRequest request);

    /** 提交审批（必填校验 → 发起流程 → 状态 1） */
    void submitContract(Long id);

    /**
     * 终态通过定稿（事件回调调用，事务内）：
     * rendered_html 快照 + 水印 PDF 归档 sys_attachment + 发编号 → CAS 落状态 2。
     * CAS 未命中（已定稿/状态漂移）幂等跳过。
     */
    void finalizeApproved(CtContract contract);
}
