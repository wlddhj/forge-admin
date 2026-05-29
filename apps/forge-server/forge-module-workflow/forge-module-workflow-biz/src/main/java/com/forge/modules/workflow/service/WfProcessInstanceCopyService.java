package com.forge.modules.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.workflow.dto.copy.CopyQueryRequest;
import com.forge.modules.workflow.dto.copy.CopyResponse;

public interface WfProcessInstanceCopyService {

    Page<CopyResponse> pageCopy(CopyQueryRequest request);

    /**
     * 流程结束时自动抄送
     *
     * @param processInstanceId    流程实例ID
     * @param processDefinitionId  流程定义ID
     * @param reason               抄送原因
     */
    void autoCopyOnProcessEnd(String processInstanceId, String processDefinitionId, String reason);
}
