package com.forge.modules.contract.controller.admin;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 合同清单导出行（easyexcel）
 *
 * @author forge-admin
 */
@Data
public class ContractExportRow {

    @ExcelProperty("合同编号")
    private String contractNo;

    @ExcelProperty("合同标题")
    private String title;

    @ExcelProperty("分类")
    private String categoryCode;

    @ExcelProperty("模板")
    private String templateName;

    @ExcelProperty("版本号")
    private Integer templateVersionNo;

    @ExcelProperty("状态")
    private String statusLabel;

    @ExcelProperty("创建人")
    private Long createBy;

    @ExcelProperty("创建时间")
    private LocalDateTime createTime;
}
