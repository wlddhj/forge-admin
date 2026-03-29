package com.forge.admin.modules.system.dto.log;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 登录日志导出 DTO
 */
@Data
public class LoginLogExport {

    @ExcelProperty("用户名")
    @ColumnWidth(15)
    private String username;

    @ExcelProperty("登录IP")
    @ColumnWidth(18)
    private String loginIp;

    @ExcelProperty("登录地点")
    @ColumnWidth(15)
    private String loginLocation;

    @ExcelProperty("浏览器")
    @ColumnWidth(18)
    private String browser;

    @ExcelProperty("操作系统")
    @ColumnWidth(15)
    private String os;

    @ExcelProperty("登录状态")
    @ColumnWidth(10)
    private String status;

    @ExcelProperty("提示消息")
    @ColumnWidth(20)
    private String msg;

    @ExcelProperty("登录时间")
    @ColumnWidth(22)
    private String loginTime;
}
