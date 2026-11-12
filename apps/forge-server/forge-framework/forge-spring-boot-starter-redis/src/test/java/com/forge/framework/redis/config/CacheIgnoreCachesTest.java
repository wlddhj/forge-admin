package com.forge.framework.redis.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 跨租户共享缓存清单单元测试（租户开闸前置 T9）：
 * fileConfig（共享表）不再被租户前缀烘焙；yml ignore-caches 可增配。
 */
class CacheIgnoreCachesTest {

    @Test
    @DisplayName("默认共享清单含 fileConfig（sys_file_config 为 ignore-tables 共享表）")
    void defaultIgnoreCachesShouldContainFileConfig() {
        var ignore = CacheConfig.resolveIgnoreCaches(List.of());
        assertTrue(ignore.contains("fileConfig"), "fileConfig 不得被租户前缀化");
        assertTrue(ignore.contains("userAuth"));
        assertTrue(ignore.contains("dictData"));
    }

    @Test
    @DisplayName("yml 配置可增配共享缓存名（并集语义）")
    void configuredCachesShouldMerge() {
        var ignore = CacheConfig.resolveIgnoreCaches(List.of(" myExtra ", ""));
        assertTrue(ignore.contains("myExtra"));
        assertTrue(ignore.contains("fileConfig"), "增配不覆盖默认清单");
        assertFalse(ignore.contains(""));
    }
}
