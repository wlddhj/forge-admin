package com.forge.modules.workflow.dto.bizview;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 业务单据视图字段（label/value 只读展示单元）
 *
 * @author forge-admin
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WfBizViewField {

    /** 字段展示名（如「合同标题」） */
    private String label;

    /** 字段值（渲染端按字符串展示，空值由前端显示为 -） */
    private String value;
}
