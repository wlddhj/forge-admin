package com.forge.admin.modules.system.dto.role;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色导出 DTO
 *
 * @author standadmin
 */
@Data
public class RoleExport {

    @ExcelProperty("角色名称")
    @ColumnWidth(20)
    private String roleName;

    @ExcelProperty("角色编码")
    @ColumnWidth(20)
    private String roleCode;

    @ExcelProperty("描述")
    @ColumnWidth(30)
    private String description;

    @ExcelProperty("状态")
    @ColumnWidth(10)
    private String status;

    @ExcelProperty("创建时间")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
