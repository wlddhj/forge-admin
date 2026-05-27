package com.forge.admin.modules.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.admin.common.exception.BusinessException;
import com.forge.admin.common.response.ResultCode;
import com.forge.admin.modules.workflow.dto.expression.ExpressionQueryRequest;
import com.forge.admin.modules.workflow.dto.expression.ExpressionRequest;
import com.forge.admin.modules.workflow.dto.expression.ExpressionResponse;
import com.forge.admin.modules.workflow.entity.WfProcessExpression;
import com.forge.admin.modules.workflow.mapper.WfProcessExpressionMapper;
import com.forge.admin.modules.workflow.service.WfProcessExpressionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WfProcessExpressionServiceImpl extends ServiceImpl<WfProcessExpressionMapper, WfProcessExpression>
        implements WfProcessExpressionService {

    private final WfProcessExpressionMapper expressionMapper;

    @Override
    public Page<ExpressionResponse> pageExpressions(ExpressionQueryRequest request) {
        Page<WfProcessExpression> page = new Page<>(request.getPageNum(), request.getPageSize());

        LambdaQueryWrapper<WfProcessExpression> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(request.getName()), WfProcessExpression::getName, request.getName())
                .eq(request.getStatus() != null, WfProcessExpression::getStatus, request.getStatus())
                .orderByDesc(WfProcessExpression::getCreateTime);

        Page<WfProcessExpression> resultPage = expressionMapper.selectPage(page, wrapper);

        Page<ExpressionResponse> responsePage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        responsePage.setRecords(resultPage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList()));
        return responsePage;
    }

    @Override
    public List<ExpressionResponse> listAllEnabled() {
        LambdaQueryWrapper<WfProcessExpression> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfProcessExpression::getStatus, 1)
                .orderByDesc(WfProcessExpression::getCreateTime);
        return expressionMapper.selectList(wrapper).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ExpressionResponse getExpressionDetail(Long id) {
        WfProcessExpression expression = getById(id);
        if (expression == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return convertToResponse(expression);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addExpression(ExpressionRequest request) {
        WfProcessExpression expression = new WfProcessExpression();
        BeanUtil.copyProperties(request, expression);
        if (expression.getStatus() == null) {
            expression.setStatus(1);
        }
        save(expression);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateExpression(ExpressionRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        WfProcessExpression expression = getById(request.getId());
        if (expression == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        BeanUtil.copyProperties(request, expression);
        updateById(expression);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteExpressions(List<Long> ids) {
        removeByIds(ids);
    }

    private ExpressionResponse convertToResponse(WfProcessExpression expression) {
        ExpressionResponse response = new ExpressionResponse();
        BeanUtil.copyProperties(expression, response);
        return response;
    }
}
