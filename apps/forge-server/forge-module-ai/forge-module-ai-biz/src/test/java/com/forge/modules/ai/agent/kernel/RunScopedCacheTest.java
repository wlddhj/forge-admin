package com.forge.modules.ai.agent.kernel;

import com.forge.modules.ai.entity.AiAgentConfig;
import com.forge.modules.ai.entity.AiModelConfig;
import com.forge.modules.ai.mapper.AiAgentConfigMapper;
import com.forge.modules.ai.mapper.AiModelConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * run 级配置缓存单测：重复取同一配置仅查库一次（AC3）
 */
class RunScopedCacheTest {

    private AiAgentConfigMapper agentMapper;
    private AiModelConfigMapper modelMapper;
    private RunScopedCache cache;

    @BeforeEach
    void setUp() {
        agentMapper = mock(AiAgentConfigMapper.class);
        modelMapper = mock(AiModelConfigMapper.class);
        cache = new RunScopedCache(agentMapper, modelMapper);
    }

    @Test
    void 同一Agent多次获取仅查库一次() {
        AiAgentConfig config = new AiAgentConfig();
        config.setId(7L);
        when(agentMapper.selectById(7L)).thenReturn(config);

        assertEquals(config, cache.agent(7L));
        assertEquals(config, cache.agent(7L));
        assertEquals(config, cache.agent(7L));
        verify(agentMapper, times(1)).selectById(7L);
    }

    @Test
    void 同一模型多次获取仅查库一次() {
        AiModelConfig model = new AiModelConfig();
        model.setId(3L);
        when(modelMapper.selectById(3L)).thenReturn(model);

        assertEquals(model, cache.model(3L));
        assertEquals(model, cache.model(3L));
        verify(modelMapper, times(1)).selectById(3L);
    }

    @Test
    void 按类型获取缓存后不再查库() {
        AiModelConfig model = new AiModelConfig();
        model.setId(5L);
        when(modelMapper.selectList(any())).thenReturn(List.of(model));

        assertEquals(model, cache.modelByType("text"));
        assertEquals(model, cache.modelByType("text"));
        verify(modelMapper, times(1)).selectList(any());
    }

    @Test
    void 空入参返回null不查库() {
        assertNull(cache.agent(null));
        assertNull(cache.model(null));
        assertNull(cache.modelByType(null));
        assertNull(cache.modelByType(" "));
        verify(agentMapper, times(0)).selectById(any());
    }
}
