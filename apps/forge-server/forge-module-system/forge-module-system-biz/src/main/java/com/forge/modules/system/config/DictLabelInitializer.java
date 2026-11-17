package com.forge.modules.system.config;

import com.forge.modules.system.service.SysDictDataService;
import com.forge.modules.system.util.DictLabelUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * DictLabelUtils 装配器：容器启动时把字典服务注入静态工具。
 * <p>
 * 构造注入拿到的是带 @Cacheable 代理的 Bean，静态调用链路保留 Redis 二级缓存；
 * 若改在 SysDictDataServiceImpl 内 init(this)（@PostConstruct 时机对象尚未代理），
 * 将绕过 Redis 缓存直连 DB——故必须由本外部 Bean 完成 init。
 *
 * @author forge-admin
 */
@Component
@RequiredArgsConstructor
public class DictLabelInitializer {

    private final SysDictDataService dictDataService;

    @PostConstruct
    public void init() {
        DictLabelUtils.init(dictDataService);
    }
}
