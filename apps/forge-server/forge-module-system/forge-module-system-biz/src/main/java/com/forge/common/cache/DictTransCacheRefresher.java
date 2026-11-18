package com.forge.common.cache;

import cn.hutool.core.util.StrUtil;
import com.forge.modules.system.dto.dict.DictDataResponse;
import com.forge.modules.system.dto.dict.DictTypeResponse;
import com.forge.modules.system.service.SysDictDataService;
import com.forge.modules.system.service.SysDictTypeService;
import lombok.extern.slf4j.Slf4j;
import org.dromara.trans.service.impl.DictionaryTransService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * easy-trans 字典翻译缓存（{@code TransType.DICTIONARY}）刷新唯一出口。
 * <p>
 * 字典写路径（增删改/停启/批量保存/手动刷新）经本类在事务 afterCommit 后重建
 * {@link DictionaryTransService} 的 type → (value → label) 翻译缓存；多实例一致性由
 * 框架内建 Redis pub/sub（{@code refreshCacheAndNoticeOtherService}）解决，不自建广播。
 * <p>
 * 刷新时经 {@code SysDictDataService} 的 Spring 代理重查（{@code @Cacheable("dictData")}）：
 * afterCommit 时写事务的 {@code @CacheEvict} 已生效，重查既取到新数据又顺带回填 Redis 缓存。
 * <p>
 * 构造参数对两个字典服务标 {@link Lazy}：字典 ServiceImpl 构造注入本类，存在
 * refresher → service → refresher 的构造环，{@code @Lazy} 推迟解析到首次调用以断环；
 * 单测可直接传 mock，不受影响。
 *
 * @author forge-admin
 */
@Slf4j
@Component
public class DictTransCacheRefresher {

    private final SysDictDataService sysDictDataService;
    private final SysDictTypeService sysDictTypeService;
    private final DictionaryTransService dictionaryTransService;

    public DictTransCacheRefresher(@Lazy SysDictDataService sysDictDataService,
                                   @Lazy SysDictTypeService sysDictTypeService,
                                   DictionaryTransService dictionaryTransService) {
        this.sysDictDataService = sysDictDataService;
        this.sysDictTypeService = sysDictTypeService;
        this.dictionaryTransService = dictionaryTransService;
    }

    /**
     * 事务提交后按类型刷新翻译缓存（无事务上下文时立即执行）。
     * 空白类型忽略、重复类型去重；单类型刷新失败只记日志不中断其余类型。
     */
    public void refreshTypesAfterCommit(String... dictTypes) {
        if (dictTypes == null || dictTypes.length == 0) {
            return;
        }
        List<String> types = Arrays.stream(dictTypes)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
        if (types.isEmpty()) {
            return;
        }
        runAfterCommit(() -> types.forEach(this::refreshOne));
    }

    /**
     * 事务提交后移除指定类型的翻译缓存组（字典类型删除/编码变更时用；
     * 与刷新不同——组已失效，重查无意义，直接移除）。
     */
    public void removeTypeAfterCommit(String dictType) {
        if (StrUtil.isBlank(dictType)) {
            return;
        }
        runAfterCommit(() -> {
            try {
                dictionaryTransService.removeDictGroupAndNoticeOtherService(dictType);
            } catch (Exception e) {
                log.warn("[removeType][字典翻译缓存组移除失败: dictType={}]", dictType, e);
            }
        });
    }

    /**
     * 全量重建所有启用类型的翻译缓存（手动「刷新字典缓存」入口与启动加载共用，
     * 立即执行、逐类型幂等）。枚举失败（如启动期 DB 未就绪）只记日志不外抛，
     * 不阻断 ApplicationReadyEvent 传播。
     */
    public void refreshAll() {
        List<String> types;
        try {
            types = sysDictTypeService.getAllDictTypes().stream()
                    .map(DictTypeResponse::getDictType)
                    .toList();
        } catch (Exception e) {
            log.warn("[refreshAll][字典类型枚举失败，翻译缓存重建跳过]", e);
            return;
        }
        types.forEach(this::refreshOne);
    }

    private void refreshOne(String dictType) {
        try {
            List<DictDataResponse> dataList = sysDictDataService.getDictDataByType(dictType);
            Map<String, String> transMap = new HashMap<>();
            if (dataList != null) {
                // 同码值以 dictSort 靠前者优先（服务端已按 dictSort 升序返回启用项）
                for (DictDataResponse data : dataList) {
                    if (data.getDictValue() != null && data.getDictLabel() != null) {
                        transMap.putIfAbsent(data.getDictValue(), data.getDictLabel());
                    }
                }
            }
            dictionaryTransService.refreshCacheAndNoticeOtherService(dictType, transMap);
        } catch (Exception e) {
            // afterCommit 回调内异常不外抛：事务已提交，失败只降级为翻译陈旧，待下次刷新收敛
            log.warn("[refreshOne][字典翻译缓存刷新失败: dictType={}]", dictType, e);
        }
    }

    /**
     * 事务提交后执行（对齐 UserAuthCacheServiceImpl 失效钩子范式）；无事务时立即执行。
     */
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
