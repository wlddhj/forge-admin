package com.forge.admin.modules.system.dto.user;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

/**
 * 用户导入 DTO
 *
 * @author standadmin
 */
@Data
public class UserImportDTO {

    @ExcelProperty("用户名")
    @ColumnWidth(15)
    private String username;

    @ExcelProperty("昵称")
    @ColumnWidth(15)
    private String nickname;

    @ExcelProperty("手机号")
    @ColumnWidth(15)
    private String phone;

    @ExcelProperty("邮箱")
    @ColumnWidth(25)
    private String email;

    @ExcelProperty("部门ID")
    @ColumnWidth(10)
    private Long deptId;

    @ExcelProperty("状态")
    @ColumnWidth(10)
    private Integer status;
}
