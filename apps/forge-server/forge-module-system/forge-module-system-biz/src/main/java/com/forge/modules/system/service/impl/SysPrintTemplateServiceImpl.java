package com.forge.modules.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.forge.common.exception.BusinessException;
import com.forge.common.response.ResultCode;
import com.forge.modules.system.dto.print.PrintTemplateQueryRequest;
import com.forge.modules.system.dto.print.PrintTemplateRequest;
import com.forge.modules.system.dto.print.PrintTemplateResponse;
import com.forge.modules.system.entity.SysPrintTemplate;
import com.forge.modules.system.mapper.SysPrintTemplateMapper;
import com.forge.modules.system.service.SysPrintTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 打印模板服务实现
 */
@Service
@RequiredArgsConstructor
public class SysPrintTemplateServiceImpl extends ServiceImpl<SysPrintTemplateMapper, SysPrintTemplate> implements SysPrintTemplateService {

    @Override
    public Page<PrintTemplateResponse> pagePrintTemplates(PrintTemplateQueryRequest request) {
        Page<SysPrintTemplate> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<SysPrintTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(request.getTemplateCode()), SysPrintTemplate::getTemplateCode, request.getTemplateCode())
                .like(StrUtil.isNotBlank(request.getTemplateName()), SysPrintTemplate::getTemplateName, request.getTemplateName())
                .eq(request.getStatus() != null, SysPrintTemplate::getStatus, request.getStatus())
                .orderByDesc(SysPrintTemplate::getId);

        Page<SysPrintTemplate> templatePage = page(page, wrapper);

        Page<PrintTemplateResponse> responsePage = new Page<>();
        responsePage.setCurrent(templatePage.getCurrent());
        responsePage.setSize(templatePage.getSize());
        responsePage.setTotal(templatePage.getTotal());
        responsePage.setRecords(templatePage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList()));
        return responsePage;
    }

    @Override
    public PrintTemplateResponse getPrintTemplateDetail(Long id) {
        SysPrintTemplate template = getById(id);
        if (template == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return convertToResponse(template);
    }

    @Override
    public PrintTemplateResponse getPrintTemplateByCode(String code) {
        SysPrintTemplate template = lambdaQuery()
                .eq(SysPrintTemplate::getTemplateCode, code)
                .last("LIMIT 1")
                .one();
        return template == null ? null : convertToResponse(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addPrintTemplate(PrintTemplateRequest request) {
        if (lambdaQuery().eq(SysPrintTemplate::getTemplateCode, request.getTemplateCode()).exists()) {
            throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "模板编号已存在");
        }
        SysPrintTemplate template = new SysPrintTemplate();
        BeanUtil.copyProperties(request, template);
        save(template);
        return template.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePrintTemplate(PrintTemplateRequest request) {
        SysPrintTemplate template = getById(request.getId());
        if (template == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        if (!template.getTemplateCode().equals(request.getTemplateCode())) {
            if (lambdaQuery().eq(SysPrintTemplate::getTemplateCode, request.getTemplateCode()).exists()) {
                throw new BusinessException(ResultCode.DATA_EXISTS.getCode(), "模板编号已存在");
            }
        }
        BeanUtil.copyProperties(request, template);
        updateById(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePrintTemplates(List<Long> ids) {
        removeByIds(ids);
    }

    private PrintTemplateResponse convertToResponse(SysPrintTemplate template) {
        PrintTemplateResponse response = new PrintTemplateResponse();
        BeanUtil.copyProperties(template, response);
        return response;
    }
}
