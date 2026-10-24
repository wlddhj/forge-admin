package com.forge.modules.ai.service;

import com.forge.modules.ai.mapper.AiApiUsageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用量记账单测：成功/失败参数、日聚合键、异常静默（AC4）
 */
class AiUsageRecorderTest {

    private AiApiUsageMapper mapper;
    private AiUsageRecorder recorder;

    @BeforeEach
    void setUp() {
        mapper = mock(AiApiUsageMapper.class);
        when(mapper.upsertIncrement(anyLong(), anyLong(), any(LocalDate.class), anyInt(), anyInt(),
                anyInt(), anyInt(), anyInt())).thenReturn(1);
        recorder = new AiUsageRecorder(mapper);
    }

    @Test
    void 成功调用记账参数() {
        recorder.record(7L, 3L, true, 100, 200, 350);

        verify(mapper).upsertIncrement(7L, 3L, LocalDate.now(), 1, 0, 100, 200, 350);
    }

    @Test
    void 失败调用只记请求与失败() {
        recorder.record(7L, 3L, false, 0, 0, 900);

        verify(mapper).upsertIncrement(7L, 3L, LocalDate.now(), 0, 1, 0, 0, 900);
    }

    @Test
    void 负数Token与时延防御归零() {
        recorder.record(7L, 3L, true, -5, -10, -1);

        verify(mapper).upsertIncrement(7L, 3L, LocalDate.now(), 1, 0, 0, 0, 0);
    }

    @Test
    void upsert异常静默不外抛() {
        doThrow(new RuntimeException("db down")).when(mapper)
                .upsertIncrement(anyLong(), anyLong(), any(LocalDate.class), anyInt(), anyInt(),
                        anyInt(), anyInt(), anyInt());
        recorder.record(7L, 3L, true, 1, 1, 1); // 不抛即通过
    }

    @Test
    void 多次调用按日聚合键累加() {
        recorder.record(7L, 3L, true, 10, 20, 100);
        recorder.record(7L, 3L, false, 0, 0, 50);
        recorder.record(8L, 3L, true, 1, 1, 10);

        ArgumentCaptor<Long> userCaptor = ArgumentCaptor.forClass(Long.class);
        verify(mapper, org.mockito.Mockito.times(3))
                .upsertIncrement(userCaptor.capture(), anyLong(), any(LocalDate.class),
                        anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        assertEquals(java.util.List.of(7L, 7L, 8L), userCaptor.getAllValues());
    }
}
