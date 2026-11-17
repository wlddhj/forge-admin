package com.forge.modules.system.service.impl;

import com.forge.common.exception.BusinessException;
import com.forge.modules.system.auth.util.FileUploadValidator;
import com.forge.modules.system.entity.SysAttachment;
import com.forge.modules.system.entity.SysFileConfig;
import com.forge.modules.system.entity.SysUser;
import com.forge.modules.system.mapper.SysAttachmentMapper;
import com.forge.modules.system.service.SysFileConfigService;
import com.forge.modules.system.service.SysUserService;
import com.forge.modules.system.service.spi.AttachmentRefChecker;
import com.forge.modules.system.service.storage.LocalStorageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流式上传共核与 loadContent 的 local 存储真文件路径验证
 */
class SysAttachmentServiceImplTest {

    @TempDir
    Path tempDir;

    private SysAttachmentMapper attachmentMapper;
    private SysUserService userService;
    private SysFileConfigService fileConfigService;
    private FileUploadValidator validator;
    private SysAttachmentServiceImpl service;

    @BeforeEach
    void setUp() {
        attachmentMapper = mock(SysAttachmentMapper.class);
        userService = mock(SysUserService.class);
        fileConfigService = mock(SysFileConfigService.class);
        validator = mock(FileUploadValidator.class);
        service = new SysAttachmentServiceImpl(attachmentMapper, userService, fileConfigService,
                validator, List.of(new LocalStorageClient(new ServerProperties())), List.of());
        service.initClientMap();
        // 无登录上下文：上传人回退 system
        when(userService.getCurrentUser()).thenThrow(new IllegalStateException("no context"));
    }

    private SysFileConfig localConfig() {
        SysFileConfig config = new SysFileConfig();
        config.setId(7L);
        config.setStorageType("local");
        config.setBasePath(tempDir.toString());
        return config;
    }

    @Test
    void streamUploadWritesFileAndRecord() {
        doReturn(localConfig()).when(fileConfigService).getDefaultConfig();
        doReturn(1).when(attachmentMapper).insert(any(SysAttachment.class));

        byte[] content = "定稿PDF内容".getBytes();
        service.upload(new ByteArrayInputStream(content), content.length, "application/pdf",
                "contract-1.pdf", "contract", 1L);

        ArgumentCaptor<SysAttachment> captor = ArgumentCaptor.forClass(SysAttachment.class);
        verify(attachmentMapper).insert(captor.capture());
        SysAttachment saved = captor.getValue();
        assertThat(saved.getOriginalName()).isEqualTo("contract-1.pdf");
        assertThat(saved.getFileSize()).isEqualTo(content.length);
        assertThat(saved.getFileType()).isEqualTo("application/pdf");
        assertThat(saved.getFileExtension()).isEqualTo("pdf");
        assertThat(saved.getStorageType()).isEqualTo("local");
        assertThat(saved.getFileConfigId()).isEqualTo(7L);
        assertThat(saved.getBizType()).isEqualTo("contract");
        assertThat(saved.getBizId()).isEqualTo(1L);
        assertThat(saved.getUploaderName()).isEqualTo("system");
        // 物理文件已落 basePath/yyyy/MM/dd/uuid.pdf
        assertThat(Files.exists(Path.of(saved.getFilePath()))).isTrue();
        assertThat(saved.getFileUrl()).endsWith(".pdf");
    }

    @Test
    void multipartUploadRejectsValidationError() {
        when(validator.validate(any())).thenReturn("不允许的文件类型：exe");
        var file = spy(org.springframework.web.multipart.MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);

        assertThatThrownBy(() -> service.upload(file, "avatar", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许的文件类型");
    }

    @Test
    void loadContentReadsLocalFile() throws Exception {
        Path file = tempDir.resolve("legacy.pdf");
        Files.write(file, "bytes".getBytes());
        SysAttachment attachment = new SysAttachment();
        attachment.setId(9L);
        attachment.setStorageType("local");
        attachment.setFilePath(file.toString());
        doReturn(attachment).when(attachmentMapper).selectById(9L);

        assertThat(service.loadContent(9L)).isEqualTo("bytes".getBytes());
    }

    @Test
    void loadContentMissingAttachmentRejected() {
        doReturn(null).when(attachmentMapper).selectById(404L);
        assertThatThrownBy(() -> service.loadContent(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件不存在");
    }

    @Test
    void loadContentLocalFileMissingRejected() {
        SysAttachment attachment = new SysAttachment();
        attachment.setId(10L);
        attachment.setStorageType("local");
        attachment.setFilePath(tempDir.resolve("not-exist.pdf").toString());
        doReturn(attachment).when(attachmentMapper).selectById(10L);

        assertThatThrownBy(() -> service.loadContent(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("读取附件内容失败");
    }

    @Test
    void currentUserFallbackKeepsSystemUploader() {
        doReturn(localConfig()).when(fileConfigService).getDefaultConfig();
        doReturn(1).when(attachmentMapper).insert(any(SysAttachment.class));

        SysUser user = new SysUser();
        user.setId(3L);
        user.setNickname("张三");
        // 已有 thenThrow 桩的方法再桩须用 doReturn（when() 会先触发旧桩抛出）
        doReturn(user).when(userService).getCurrentUser();

        byte[] content = "x".getBytes();
        service.upload(new ByteArrayInputStream(content), content.length, "text/plain", "a.txt", null, null);

        ArgumentCaptor<SysAttachment> captor = ArgumentCaptor.forClass(SysAttachment.class);
        verify(attachmentMapper).insert(captor.capture());
        assertThat(captor.getValue().getUploaderId()).isEqualTo(3L);
        assertThat(captor.getValue().getUploaderName()).isEqualTo("张三");
    }

    @Test
    void deleteBlockedWhenReferenced() {
        AttachmentRefChecker checker = mock(AttachmentRefChecker.class);
        when(checker.bizType()).thenReturn("notice");
        when(checker.checkReferenced(any(SysAttachment.class))).thenReturn("公告『放假通知』");
        SysAttachmentServiceImpl guarded = new SysAttachmentServiceImpl(attachmentMapper, userService,
                fileConfigService, validator, List.of(), List.of(checker));
        guarded.initClientMap();

        SysAttachment noticeAttachment = new SysAttachment();
        noticeAttachment.setId(1L);
        noticeAttachment.setOriginalName("通知附件.pdf");
        noticeAttachment.setBizType("notice");
        doReturn(List.of(noticeAttachment)).when(attachmentMapper).selectBatchIds(List.of(1L));

        assertThatThrownBy(() -> guarded.deleteAttachments(List.of(1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("公告『放假通知』");
        verify(attachmentMapper, never()).deleteById(1L);
    }

    @Test
    void deleteProceedsWhenCheckerPasses() {
        AttachmentRefChecker checker = mock(AttachmentRefChecker.class);
        when(checker.bizType()).thenReturn("notice");
        when(checker.checkReferenced(any(SysAttachment.class))).thenReturn(null);
        SysAttachmentServiceImpl guarded = new SysAttachmentServiceImpl(attachmentMapper, userService,
                fileConfigService, validator, List.of(), List.of(checker));
        guarded.initClientMap();

        SysAttachment noticeAttachment = new SysAttachment();
        noticeAttachment.setId(2L);
        noticeAttachment.setOriginalName("通知附件.pdf");
        noticeAttachment.setBizType("notice");
        noticeAttachment.setStorageType("local");
        doReturn(List.of(noticeAttachment)).when(attachmentMapper).selectBatchIds(List.of(2L));

        guarded.deleteAttachments(List.of(2L));
        verify(attachmentMapper).deleteById(2L);
    }
}
