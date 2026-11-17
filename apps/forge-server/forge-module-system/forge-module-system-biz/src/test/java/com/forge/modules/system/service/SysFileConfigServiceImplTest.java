package com.forge.modules.system.service;

import com.forge.common.exception.BusinessException;
import com.forge.modules.system.dto.file.FileConfigRequest;
import com.forge.modules.system.entity.SysFileConfig;
import com.forge.modules.system.service.impl.SysFileConfigServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * 编辑配置的密钥保留语义：列表回显 AccessKey 为脱敏值、SecretKey 不回显，
 * 空值/掩码不得覆盖库中原值（否则编辑一次就把云存储密钥毁掉）
 */
class SysFileConfigServiceImplTest {

    private SysFileConfigServiceImpl serviceSpy() {
        return spy(new SysFileConfigServiceImpl(java.util.List.of()));
    }

    private SysFileConfig existing() {
        SysFileConfig config = new SysFileConfig();
        config.setId(1L);
        config.setConfigName("minio-prod");
        config.setStorageType("minio");
        config.setAccessKey("realAccessKey");
        config.setSecretKey("realSecretKey");
        config.setIsDefault(0);
        config.setStatus(1);
        return config;
    }

    private FileConfigRequest request(String accessKey, String secretKey) {
        FileConfigRequest request = new FileConfigRequest();
        request.setId(1L);
        request.setConfigName("minio-prod-renamed");
        request.setStorageType("minio");
        request.setAccessKey(accessKey);
        request.setSecretKey(secretKey);
        request.setIsDefault(0);
        request.setStatus(1);
        return request;
    }

    private SysFileConfig updateFileConfig(SysFileConfigServiceImpl spy, FileConfigRequest request) {
        doReturn(existing()).when(spy).getById(1L);
        doReturn(true).when(spy).updateById(any(SysFileConfig.class));
        spy.updateFileConfig(request);
        ArgumentCaptor<SysFileConfig> captor = ArgumentCaptor.forClass(SysFileConfig.class);
        verify(spy).updateById(captor.capture());
        return captor.getValue();
    }

    @Test
    void blankKeysKeepStoredSecrets() {
        SysFileConfig saved = updateFileConfig(serviceSpy(), request("", null));
        assertThat(saved.getAccessKey()).isEqualTo("realAccessKey");
        assertThat(saved.getSecretKey()).isEqualTo("realSecretKey");
    }

    @Test
    void maskedAccessKeyKeepsStoredOne() {
        SysFileConfig saved = updateFileConfig(serviceSpy(), request("real****", ""));
        assertThat(saved.getAccessKey()).isEqualTo("realAccessKey");
        assertThat(saved.getSecretKey()).isEqualTo("realSecretKey");
    }

    @Test
    void freshKeysOverride() {
        SysFileConfig saved = updateFileConfig(serviceSpy(), request("newAK", "newSK"));
        assertThat(saved.getAccessKey()).isEqualTo("newAK");
        assertThat(saved.getSecretKey()).isEqualTo("newSK");
    }

    @Test
    void missingConfigRejected() {
        SysFileConfigServiceImpl spy = serviceSpy();
        doReturn(null).when(spy).getById(404L);
        FileConfigRequest request = request("", "");
        request.setId(404L);
        assertThatThrownBy(() -> spy.updateFileConfig(request))
                .isInstanceOf(BusinessException.class);
    }
}
