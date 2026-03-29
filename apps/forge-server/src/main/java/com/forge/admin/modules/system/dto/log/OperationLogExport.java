package com.forge.admin.modules.system.dto.log;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 操作日志导出 DTO
 */
@Data
public class OperationLogExport {

    @ExcelProperty("操作模块")
    @ColumnWidth(20)
    private String title;

    @ExcelProperty("业务类型")
    @ColumnWidth(12)
    private String businessType;

    @ExcelProperty("请求方式")
    @ColumnWidth(12)
    private String requestMethod;

    @ExcelProperty("操作人")
    @ColumnWidth(15)
    private String operatorName;

    @ExcelProperty("部门")
    @ColumnWidth(15)
    private String deptName;

    @ExcelProperty("操作IP")
    @ColumnWidth(18)
    private String operateIp;

    @ExcelProperty("操作地点")
    @ColumnWidth(15)
    private String operateLocation;

    @ExcelProperty("状态")
    @ColumnWidth(10)
    private String status;

    @ExcelProperty("操作时间")
    @ColumnWidth(22)
    private String operateTime;

    @ExcelProperty("耗时(ms)")
    @ColumnWidth(12)
    private Long costTime;
}
