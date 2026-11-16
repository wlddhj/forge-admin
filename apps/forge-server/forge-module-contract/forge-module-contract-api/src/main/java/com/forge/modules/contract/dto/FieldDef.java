package com.forge.modules.contract.dto;

import lombok.Data;

import java.util.List;

/**
 * 合同模板变量定义（field_defs_json 的元素结构）
 *
 * @author forge-admin
 */
@Data
public class FieldDef {

    /** 字段类型：单行文本 */
    public static final String TYPE_TEXT = "text";
    /** 字段类型：多行文本 */
    public static final String TYPE_TEXTAREA = "textarea";
    /** 字段类型：数字 */
    public static final String TYPE_NUMBER = "number";
    /** 字段类型：日期 */
    public static final String TYPE_DATE = "date";
    /** 字段类型：下拉（options 取值） */
    public static final String TYPE_SELECT = "select";

    /** 变量 key（对应模板芯片 data-field） */
    private String key;

    /** 变量显示名 */
    private String label;

    /** 表单控件类型 text/textarea/number/date/select */
    private String type;

    /** 是否必填 */
    private Boolean required;

    /** select 类型的候选项 */
    private List<String> options;
}
