package com.forge.modules.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.system.dto.print.PrintTemplateQueryRequest;
import com.forge.modules.system.dto.print.PrintTemplateRequest;
import com.forge.modules.system.dto.print.PrintTemplateResponse;

/**
 * 打印模板服务接口
 */
public interface SysPrintTemplateService {

    /**
     * 分页查询打印模板
     */
    Page<PrintTemplateResponse> pagePrintTemplates(PrintTemplateQueryRequest request);

    /**
     * 获取打印模板详情
     */
    PrintTemplateResponse getPrintTemplateDetail(Long id);

    /**
     * 按编号查询打印模板
     */
    PrintTemplateResponse getPrintTemplateByCode(String code);

    /**
     * 新增打印模板
     */
    Long addPrintTemplate(PrintTemplateRequest request);

    /**
     * 更新打印模板
     */
    void updatePrintTemplate(PrintTemplateRequest request);

    /**
     * 删除打印模板
     */
    void deletePrintTemplates(java.util.List<Long> ids);
}
