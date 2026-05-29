package com.forge.modules.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.forge.modules.workflow.dto.form.FormQueryRequest;
import com.forge.modules.workflow.dto.form.FormRequest;
import com.forge.modules.workflow.dto.form.FormResponse;
import com.forge.modules.workflow.dto.form.FormSimpleResponse;

import java.util.List;

/**
 * 表单管理服务接口
 *
 * @author forge
 */
public interface WfFormService {

    /**
     * 分页查询表单
     *
     * @param request 查询参数
     * @return 分页结果
     */
    Page<FormResponse> pageForms(FormQueryRequest request);

    /**
     * 获取所有启用的表单简要信息（用于下拉选择）
     *
     * @return 表单简要列表
     */
    List<FormSimpleResponse> listAllSimple();

    /**
     * 获取表单详情
     *
     * @param id 表单ID
     * @return 表单详情
     */
    FormResponse getFormDetail(Long id);

    /**
     * 新增表单
     *
     * @param request 表单请求
     */
    void addForm(FormRequest request);

    /**
     * 更新表单
     *
     * @param request 表单请求
     */
    void updateForm(FormRequest request);

    /**
     * 删除表单
     *
     * @param ids 表单ID列表
     */
    void deleteForms(List<Long> ids);
}
