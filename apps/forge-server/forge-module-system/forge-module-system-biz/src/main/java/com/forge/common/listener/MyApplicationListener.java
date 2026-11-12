package com.forge.common.listener;

import com.forge.modules.system.dto.dict.DictDataResponse;
import com.forge.modules.system.dto.dict.DictTypeResponse;
import com.forge.modules.system.service.SysDictDataService;
import com.forge.modules.system.service.SysDictTypeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.dromara.trans.service.impl.DictionaryTransService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class MyApplicationListener implements ApplicationListener<ApplicationReadyEvent> {

    @Autowired
    private DictionaryTransService dictionaryTransService;
    @Resource
    private SysDictDataService sysDictDataService;
    @Resource
    private SysDictTypeService sysDictTypeService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("初始化数据字典");
        // 处理应用启动完成事件
        List<DictTypeResponse> lstType = sysDictTypeService.getAllDictTypes();
        lstType.forEach(type -> {
            List<DictDataResponse> lstData = sysDictDataService.getDictDataByType(type.getDictType());
            //在某处将字典缓存刷新到翻译服务中，
            Map<String, String> transMap = lstData.stream().collect(Collectors.toMap(DictDataResponse::getDictValue, DictDataResponse::getDictLabel, (existing, replacement) -> existing));
            dictionaryTransService.refreshCache(type.getDictType(), transMap);
        });
        log.info("初始化数据字典完成");
    }
}