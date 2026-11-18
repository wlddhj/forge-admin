package com.forge.common.listener;

import com.forge.common.cache.DictTransCacheRefresher;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MyApplicationListener implements ApplicationListener<ApplicationReadyEvent> {

    @Resource
    private DictTransCacheRefresher dictTransCacheRefresher;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("初始化数据字典");
        // 启动期全量重建 easy-trans 字典翻译缓存（组装逻辑与写路径刷新共用同一出口；
        // 刷新变体带 pub/sub 广播，多实例各自启动时幂等无害）
        dictTransCacheRefresher.refreshAll();
        log.info("初始化数据字典完成");
    }
}
