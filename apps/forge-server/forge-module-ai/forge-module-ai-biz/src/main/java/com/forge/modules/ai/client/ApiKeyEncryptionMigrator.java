package com.forge.modules.ai.client;

import com.forge.modules.ai.entity.AiModelConfig;
import com.forge.modules.ai.mapper.AiModelConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * apiKey 存量明文加密迁移：启动时扫描库中原始值，无 ENCv1: 前缀且非空的逐条
 * 以明文回写（走 EncryptTypeHandler 自动加密），幂等——已是密文的条目不触碰。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyEncryptionMigrator implements ApplicationRunner {

    private static final String ENCRYPTED_PREFIX = "ENCv1:";

    private final AiModelConfigMapper modelConfigMapper;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Map<String, Object>> rows = modelConfigMapper.selectRawApiKeys();
            int migrated = 0;
            for (Map<String, Object> row : rows) {
                Object raw = row.get("api_key");
                if (raw == null) {
                    continue;
                }
                String value = String.valueOf(raw);
                if (value.isBlank() || value.startsWith(ENCRYPTED_PREFIX)) {
                    continue;
                }
                AiModelConfig update = new AiModelConfig();
                update.setId(((Number) row.get("id")).longValue());
                update.setApiKey(value);
                modelConfigMapper.updateById(update);
                migrated++;
            }
            if (migrated > 0) {
                log.info("apiKey 存量加密迁移完成: {} 条", migrated);
            }
        } catch (Exception e) {
            log.error("apiKey 存量加密迁移失败（不影响启动，密钥读取对明文兼容）", e);
        }
    }
}
