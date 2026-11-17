package com.forge.modules.system.service;

import com.forge.modules.system.dto.dict.DictDataResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通用数据字典转换工具（码值 → label）。
 * <p>
 * 后端任何模块需要把字典码值渲染成中文 label 时统一走本组件，
 * 不要在业务代码里各自硬编码 switch/if 映射。字典查询只含启用项
 * （SysDictDataService.getDictDataByType 已在 SQL 层过滤），异常不外抛——
 * 字典侧故障只降级为未命中，不阻断业务。
 *
 * @author forge-admin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DictLabelResolver {

    private final SysDictDataService dictDataService;

    /**
     * 按字典类型与码值解析 label
     *
     * @param dictType 字典类型（如 contract_status、wf_urgent）
     * @param value    码值（数字/字符串均可，按字符串比较）
     * @return 启用项的 label；码值为空、字典未配置或未命中返回 null（调用方自行回退）
     */
    public String resolve(String dictType, Object value) {
        if (dictType == null || dictType.isBlank() || value == null) {
            return null;
        }
        try {
            List<DictDataResponse> dictData = dictDataService.getDictDataByType(dictType);
            if (dictData == null) {
                return null;
            }
            String target = String.valueOf(value);
            return dictData.stream()
                    .filter(d -> target.equals(d.getDictValue()))
                    .map(DictDataResponse::getDictLabel)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("字典 label 解析失败，按未命中处理: dictType={}, value={}", dictType, value, e);
            return null;
        }
    }

    /**
     * 带回退的解析：未命中（或字典异常）返回 defaultValue
     */
    public String resolveOrDefault(String dictType, Object value, String defaultValue) {
        String label = resolve(dictType, value);
        return label != null ? label : defaultValue;
    }
}
