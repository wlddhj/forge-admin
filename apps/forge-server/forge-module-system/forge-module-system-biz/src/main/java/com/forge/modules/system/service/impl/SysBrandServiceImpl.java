package com.forge.modules.system.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.forge.common.exception.BusinessException;
import com.forge.modules.system.dto.brand.BrandRequest;
import com.forge.modules.system.dto.brand.BrandResponse;
import com.forge.modules.system.entity.SysConfig;
import com.forge.modules.system.mapper.SysConfigMapper;
import com.forge.modules.system.service.SysAttachmentService;
import com.forge.modules.system.service.SysBrandService;
import com.forge.modules.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SysBrandServiceImpl implements SysBrandService {

    private static final String KEY_LOGO = "sys.brand.logo";
    private static final String KEY_NAME = "sys.system.name";
    private static final String KEY_LOGIN_TITLE = "sys.brand.login.title";
    private static final String KEY_LOGIN_SUBTITLE = "sys.brand.login.subtitle";

    /**
     * 不含 svg：与附件服务 FileUploadValidator 的安全校验对齐（svg 可内嵌脚本，禁止上传）
     */
    private static final Set<String> ALLOWED_LOGO_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");
    private static final long MAX_LOGO_SIZE = 2 * 1024 * 1024L;

    private final SysConfigService sysConfigService;
    private final SysConfigMapper sysConfigMapper;
    private final SysAttachmentService sysAttachmentService;

    @Override
    public BrandResponse getBrand() {
        BrandResponse response = new BrandResponse();
        response.setLogo(StrUtil.nullToEmpty(sysConfigService.getConfigValueByKey(KEY_LOGO)));
        response.setName(StrUtil.nullToEmpty(sysConfigService.getConfigValueByKey(KEY_NAME)));
        response.setLoginTitle(StrUtil.nullToEmpty(sysConfigService.getConfigValueByKey(KEY_LOGIN_TITLE)));
        response.setLoginSubtitle(StrUtil.nullToEmpty(sysConfigService.getConfigValueByKey(KEY_LOGIN_SUBTITLE)));
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBrand(BrandRequest request) {
        upsert(KEY_LOGO, "品牌Logo", StrUtil.nullToEmpty(request.getLogo()), "brand");
        upsert(KEY_NAME, "系统名称", request.getName(), "system");
        upsert(KEY_LOGIN_TITLE, "登录页主标题", StrUtil.nullToEmpty(request.getLoginTitle()), "brand");
        upsert(KEY_LOGIN_SUBTITLE, "登录页副标题", StrUtil.nullToEmpty(request.getLoginSubtitle()), "brand");
    }

    @Override
    public String uploadLogo(MultipartFile file) {
        String extension = FileUtil.extName(file.getOriginalFilename());
        if (StrUtil.isBlank(extension) || !ALLOWED_LOGO_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(400, "仅支持 png/jpg/jpeg/webp 格式的图片");
        }
        if (file.getSize() > MAX_LOGO_SIZE) {
            throw new BusinessException(400, "Logo 图片大小不能超过 2MB");
        }
        return sysAttachmentService.upload(file, "brand-logo", null).getFileUrl();
    }

    /**
     * key 不存在时直接 save 完整实体（status=1 保证 getConfigValueByKey 的 status=1 过滤可命中；
     * 此时缓存中无该 key 条目，无需额外驱逐）
     */
    private void upsert(String configKey, String configName, String configValue, String configGroup) {
        if (sysConfigService.updateValueByKey(configKey, configValue)) {
            return;
        }
        SysConfig config = new SysConfig();
        config.setConfigName(configName);
        config.setConfigKey(configKey);
        config.setConfigValue(configValue);
        config.setConfigType("text");
        config.setConfigGroup(configGroup);
        config.setIsSystem(1);
        config.setStatus(1);
        config.setRemark("品牌配置");
        sysConfigMapper.insert(config);
    }
}
