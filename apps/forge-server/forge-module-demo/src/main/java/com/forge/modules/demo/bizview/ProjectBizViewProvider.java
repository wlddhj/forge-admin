package com.forge.modules.demo.bizview;

import com.forge.modules.demo.entity.BizProject;
import com.forge.modules.demo.mapper.BizProjectMapper;
import com.forge.modules.workflow.dto.bizview.WfBizViewData;
import com.forge.modules.workflow.dto.bizview.WfBizViewField;
import com.forge.modules.workflow.spi.WfBizViewProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 项目立项业务单据视图提供方（businessKey 前缀 project:）。
 * <p>
 * 形态A参考实现：仅结构化字段，无附件与 PDF 预览。
 *
 * @author forge-admin
 */
@Component
@RequiredArgsConstructor
public class ProjectBizViewProvider implements WfBizViewProvider {

    /** businessKey 前缀（对齐 ProjectApprovalServiceImpl.BUSINESS_KEY_PREFIX 的冒号前段） */
    public static final String BIZ_TYPE = "project";

    private final BizProjectMapper projectMapper;

    @Override
    public String bizType() {
        return BIZ_TYPE;
    }

    @Override
    public WfBizViewData getBizView(String bizId) {
        Long id;
        try {
            id = Long.parseLong(bizId);
        } catch (NumberFormatException e) {
            return null;
        }
        BizProject project = projectMapper.selectById(id);
        if (project == null) {
            return null;
        }

        List<WfBizViewField> fields = new ArrayList<>();
        fields.add(new WfBizViewField("项目名称", project.getProjectName()));
        fields.add(new WfBizViewField("项目金额", project.getTotalAmount() == null
                ? null : project.getTotalAmount().toPlainString()));
        fields.add(new WfBizViewField("责任人", project.getBizUser()));
        fields.add(new WfBizViewField("责任部门", project.getDeptName()));
        fields.add(new WfBizViewField("备注", project.getRemark()));
        fields.add(new WfBizViewField("流程编号", project.getProcessNo()));

        WfBizViewData data = new WfBizViewData();
        data.setTitle(project.getProjectName());
        data.setFields(fields);
        return data;
    }
}
