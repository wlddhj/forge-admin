package com.forge.admin.modules.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.admin.modules.workflow.dto.expression.ExpressionQueryRequest;
import com.forge.admin.modules.workflow.dto.expression.ExpressionRequest;
import com.forge.admin.modules.workflow.dto.expression.ExpressionResponse;

import java.util.List;

public interface WfProcessExpressionService {

    Page<ExpressionResponse> pageExpressions(ExpressionQueryRequest request);

    List<ExpressionResponse> listAllEnabled();

    ExpressionResponse getExpressionDetail(Long id);

    void addExpression(ExpressionRequest request);

    void updateExpression(ExpressionRequest request);

    void deleteExpressions(List<Long> ids);
}
