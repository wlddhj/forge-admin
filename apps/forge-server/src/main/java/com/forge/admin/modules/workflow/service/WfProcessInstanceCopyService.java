package com.forge.admin.modules.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.modules.workflow.dto.copy.CopyQueryRequest;
import com.forge.admin.modules.workflow.dto.copy.CopyResponse;

public interface WfProcessInstanceCopyService {

    Page<CopyResponse> pageCopy(CopyQueryRequest request);
}
