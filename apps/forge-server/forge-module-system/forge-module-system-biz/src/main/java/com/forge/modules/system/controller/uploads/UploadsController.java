package com.forge.modules.system.controller.uploads;

import cn.hutool.core.util.StrUtil;
import com.forge.modules.system.entity.SysFileConfig;
import com.forge.modules.system.service.SysFileConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地上传文件访问控制器：按请求时默认存储配置动态解析路径，
 * base_path 变更后无需重启即生效（替代启动期静态资源映射）
 *
 * @author forge-admin
 */
@Tag(name = "上传文件访问")
@RestController
@RequiredArgsConstructor
public class UploadsController {

    private final SysFileConfigService sysFileConfigService;

    @Operation(summary = "访问上传文件")
    @GetMapping({"/uploads/**", "/app-api/uploads/**"})
    public ResponseEntity<Resource> serve(HttpServletRequest request) {
        // 从 URI 直接剥离 uploads/ 前缀取相对路径：原 HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE
        // 在 Spring Framework 7 已废弃且返回完整路径，base.resolve(绝对路径) 会丢弃 base 恒 404（SB4 升级遗留，
        // 此前被租户闸 400 掩盖未暴露）
        String uri = request.getRequestURI();
        int idx = uri.indexOf("uploads/");
        String relativePath = idx >= 0 ? uri.substring(idx + "uploads/".length()) : "";
        if (StrUtil.isBlank(relativePath) || relativePath.contains("..")) {
            return ResponseEntity.notFound().build();
        }

        Path base = Paths.get(resolveBasePath()).toAbsolutePath().normalize();
        Path target = base.resolve(relativePath).normalize();
        if (!target.startsWith(base) || !Files.isReadable(target)) {
            return ResponseEntity.notFound().build();
        }

        FileSystemResource resource = new FileSystemResource(target);
        MediaType contentType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(contentType).body(resource);
    }

    private String resolveBasePath() {
        SysFileConfig config = sysFileConfigService.getDefaultConfig();
        if (config != null && "local".equals(config.getStorageType()) && StrUtil.isNotBlank(config.getBasePath())) {
            return config.getBasePath();
        }
        return "./uploads";
    }
}
