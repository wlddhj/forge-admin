package com.forge.modules.screen.service;

import com.forge.modules.screen.entity.SysScreen;
import com.forge.modules.screen.mapper.SysScreenMapper;
import com.forge.modules.screen.service.impl.SysScreenServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SysScreenServiceImpl publish 方法单元测试
 *
 * @author standadmin
 */
@ExtendWith(MockitoExtension.class)
class SysScreenServiceImplPublishTest {

    @Mock
    SysScreenMapper mapper;

    @InjectMocks
    SysScreenServiceImpl service;

    @Test
    void publish_should_copy_draft_to_config_and_keep_version_for_mp_interceptor() {
        SysScreen entity = new SysScreen();
        entity.setId(1L);
        entity.setCode("ops");
        entity.setStatus(0);
        entity.setVersion(3);
        entity.setConfigDraft("{\"cards\":[]}");
        when(mapper.selectOne(any())).thenReturn(entity);
        // 使用 doAnswer().when(mapper).updateById(any(SysScreen.class)) 形式避免
        // MyBatis Plus 3.5.7 BaseMapper 的 updateById(T) 与 updateById(Collection<T>) 重载歧义
        doAnswer(inv -> 1).when(mapper).updateById(any(SysScreen.class));

        service.publish("ops");

        // I4 修复：不再手动 +1，由 OptimisticLockerInnerInterceptor 在 SQL 层 +1，
        // 因此 entity 上的 version 仍为 3（拦截器在 SET 子句中改为 version=4，
        // 并在 WHERE 中校验 version=3）。本断言验证"未在内存中 +1"。
        ArgumentCaptor<SysScreen> captor = ArgumentCaptor.forClass(SysScreen.class);
        verify(mapper).updateById(captor.capture());
        SysScreen saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getVersion()).isEqualTo(3);
        assertThat(saved.getConfig()).isEqualTo("{\"cards\":[]}");
    }

    @Test
    void publish_should_throw_when_not_found() {
        when(mapper.selectOne(any())).thenReturn(null);
        try {
            service.publish("notexist");
            assert false : "应抛异常";
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("大屏不存在");
        }
    }
}
