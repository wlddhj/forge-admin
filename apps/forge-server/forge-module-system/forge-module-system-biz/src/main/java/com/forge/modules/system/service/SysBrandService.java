package com.forge.modules.system.service;

import com.forge.modules.system.dto.brand.BrandRequest;
import com.forge.modules.system.dto.brand.BrandResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 品牌配置服务接口
 */
public interface SysBrandService {

    /**
     * 获取品牌配置（Logo、项目名称、登录页主副标题，缺失项返回空串）
     */
    BrandResponse getBrand();

    /**
     * 保存品牌配置（按 key upsert sys_config）
     */
    void updateBrand(BrandRequest request);

    /**
     * 上传品牌 Logo 图片
     *
     * @return Logo 图片 URL
     */
    String uploadLogo(MultipartFile file);
}
