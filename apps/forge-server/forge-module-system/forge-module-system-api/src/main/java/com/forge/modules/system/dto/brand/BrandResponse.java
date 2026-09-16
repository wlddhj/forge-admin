package com.forge.modules.system.dto.brand;

import lombok.Data;

@Data
public class BrandResponse {

    /**
     * 品牌 Logo 图片 URL（空串表示使用系统默认 Logo）
     */
    private String logo;

    /**
     * 项目名称
     */
    private String name;

    /**
     * 登录页主标题
     */
    private String loginTitle;

    /**
     * 登录页副标题
     */
    private String loginSubtitle;
}
