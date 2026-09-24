package com.forge.modules.system.dto.brand;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BrandRequest {

    @Size(max = 255, message = "Logo 地址长度不能超过 255 个字符")
    private String logo;

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 50, message = "项目名称长度不能超过 50 个字符")
    private String name;

    @Size(max = 50, message = "登录页主标题长度不能超过 50 个字符")
    private String loginTitle;

    @Size(max = 100, message = "登录页副标题长度不能超过 100 个字符")
    private String loginSubtitle;

    @Valid
    private BrandTheme defaultTheme;
}
