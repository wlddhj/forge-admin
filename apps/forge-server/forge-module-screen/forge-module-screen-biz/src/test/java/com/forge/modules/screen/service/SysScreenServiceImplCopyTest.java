package com.forge.modules.screen.service;

import com.forge.modules.screen.dto.ScreenCopyRequest;
import com.forge.modules.screen.entity.SysScreen;
import com.forge.modules.screen.mapper.SysScreenMapper;
import com.forge.modules.screen.service.impl.SysScreenServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SysScreenServiceImpl copy 方法单元测试
 *
 * @author standadmin
 */
@ExtendWith(MockitoExtension.class)
class SysScreenServiceImplCopyTest {

    @Mock
    SysScreenMapper mapper;

    @InjectMocks
    SysScreenServiceImpl service;

    @Test
    void copy_should_duplicate_config_and_reset_status() {
        SysScreen src = new SysScreen();
        src.setId(1L);
        src.setCode("ops");
        src.setStatus(1);
        src.setVersion(5);
        src.setConfig("{\"cards\":[{\"id\":\"a\"}]}");
        src.setConfigDraft("{\"cards\":[{\"id\":\"a\"}]}");
        src.setTheme("dark-tech");
        when(mapper.selectOne(any())).thenReturn(src);
        // 使用 doAnswer().when(mapper).insert(any(SysScreen.class)) 形式避免
        // MyBatis Plus 3.5.7 BaseMapper 的 insert(T) 与 insert(Collection<T>) 重载歧义
        doAnswer(inv -> {
            ((SysScreen) inv.getArgument(0)).setId(99L);
            return 1;
        }).when(mapper).insert(any(SysScreen.class));

        ScreenCopyRequest req = new ScreenCopyRequest();
        req.setNewCode("ops-copy");
        req.setNewName("运维大屏副本");
        Long newId = service.copy("ops", req);

        assertThat(newId).isEqualTo(99L);
        // 通过 ArgumentCaptor 校验复制结果，规避 insert(T)/insert(Collection<T>) 重载歧义
        org.mockito.ArgumentCaptor<SysScreen> captor = org.mockito.ArgumentCaptor.forClass(SysScreen.class);
        verify(mapper).insert(captor.capture());
        SysScreen saved = captor.getValue();
        assertThat(saved.getCode()).isEqualTo("ops-copy");
        assertThat(saved.getName()).isEqualTo("运维大屏副本");
        assertThat(saved.getStatus()).isEqualTo(0);
        assertThat(saved.getVersion()).isEqualTo(1);
        assertThat(saved.getConfig()).isEqualTo("{\"cards\":[{\"id\":\"a\"}]}");
        assertThat(saved.getTheme()).isEqualTo("dark-tech");
    }
}
