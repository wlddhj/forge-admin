package com.forge.modules.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.forge.common.exception.BusinessException;
import com.forge.common.response.ResultCode;
import com.forge.modules.workflow.dto.form.FormQueryRequest;
import com.forge.modules.workflow.dto.form.FormRequest;
import com.forge.modules.workflow.dto.form.FormResponse;
import com.forge.modules.workflow.dto.form.FormSimpleResponse;
import com.forge.modules.workflow.entity.WfForm;
import com.forge.modules.workflow.mapper.WfFormMapper;
import com.forge.modules.workflow.service.WfFormService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 表单管理服务实现
 *
 * @author forge
 */
@Service
@RequiredArgsConstructor
public class WfFormServiceImpl extends ServiceImpl<WfFormMapper, WfForm> implements WfFormService {

    private final WfFormMapper wfFormMapper;

    @Override
    public Page<FormResponse> pageForms(FormQueryRequest request) {
        Page<WfForm> page = new Page<>(request.getPageNum(), request.getPageSize());

        LambdaQueryWrapper<WfForm> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(request.getName()), WfForm::getName, request.getName())
                .eq(request.getStatus() != null, WfForm::getStatus, request.getStatus())
                .orderByDesc(WfForm::getCreateTime);

        Page<WfForm> formPage = wfFormMapper.selectPage(page, wrapper);

        // 转换为响应对象
        Page<FormResponse> responsePage = new Page<>(formPage.getCurrent(), formPage.getSize(), formPage.getTotal());
        List<FormResponse> responseList = formPage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        responsePage.setRecords(responseList);

        return responsePage;
    }

    @Override
    public List<FormSimpleResponse> listAllSimple() {
        LambdaQueryWrapper<WfForm> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfForm::getStatus, 0)  // 0=正常状态
                .orderByDesc(WfForm::getCreateTime);

        List<WfForm> forms = wfFormMapper.selectList(wrapper);
        return forms.stream()
                .map(form -> {
                    FormSimpleResponse response = new FormSimpleResponse();
                    response.setId(form.getId());
                    response.setName(form.getName());
                    return response;
                })
                .collect(Collectors.toList());
    }

    @Override
    public FormResponse getFormDetail(Long id) {
        WfForm form = getById(id);
        if (form == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        return convertToResponse(form);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addForm(FormRequest request) {
        WfForm form = new WfForm();
        BeanUtil.copyProperties(request, form);
        save(form);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateForm(FormRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }

        WfForm form = getById(request.getId());
        if (form == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }

        BeanUtil.copyProperties(request, form);
        updateById(form);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteForms(List<Long> ids) {
        removeByIds(ids);
    }

    /**
     * 实体转响应对象
     */
    private FormResponse convertToResponse(WfForm form) {
        FormResponse response = new FormResponse();
        BeanUtil.copyProperties(form, response);
        return response;
    }
}
