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
                                                      String contractNo, String categoryCode, Integer status);

    /** 合同详情（含模板名/版本号） */
    ContractDtos.ContractResponse getContract(Long id);

    /** 新建草稿（锁定模板当前版本） */
    Long createContract(ContractDtos.CreateRequest request);

    /** 编辑草稿（status=3/4 时重置为 0 语义） */
    void updateContract(ContractDtos.UpdateRequest request);

    /** 删除（仅草稿态） */
    void deleteContract(Long id);

    /** 实时预览 PDF（草稿态：填充+渲染，无水印，不落盘） */
    byte[] previewPdf(Long id);

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
