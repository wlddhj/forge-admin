package com.forge.admin.modules.auth.controller;

import com.forge.admin.common.annotation.OperationLog;
import com.forge.admin.common.response.Result;
import com.forge.admin.modules.auth.dto.oauth2.ClientCreateRequest;
import com.forge.admin.modules.auth.dto.oauth2.ClientQueryRequest;
import com.forge.admin.modules.auth.dto.oauth2.ClientResponse;
import com.forge.admin.modules.auth.dto.oauth2.ClientUpdateRequest;
import com.forge.admin.modules.auth.service.OAuth2ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * OAuth2 客户端管理控制器
 */
@Slf4j
@Tag(name = "OAuth2客户端管理")
@RestController
@RequestMapping("/system/oauth2-client")
@RequiredArgsConstructor
public class OAuth2ClientController {

    private final OAuth2ClientService oAuth2ClientService;

    @Operation(summary = "查询客户端列表")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:oauth2-client:list')")
    public Result<List<ClientResponse>> list(ClientQueryRequest request) {
        return Result.success(oAuth2ClientService.listClients(request));
    }

    @Operation(summary = "查询客户端详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:oauth2-client:query')")
    public Result<ClientResponse> getInfo(@PathVariable String id) {
        return Result.success(oAuth2ClientService.getClient(id));
    }

    @Operation(summary = "新增客户端")
    @PostMapping
    @PreAuthorize("hasAuthority('system:oauth2-client:add')")
    @OperationLog(title = "OAuth2客户端管理", businessType = OperationLog.BusinessType.INSERT)
    public Result<Map<String, String>> add(@Valid @RequestBody ClientCreateRequest request) {
        String clientSecret = oAuth2ClientService.createClient(request);
        // clientSecret 仅此一次返回
        return Result.success(Map.of(
                "clientId", request.getClientId(),
                "clientSecret", clientSecret
        ));
    }

    @Operation(summary = "修改客户端")
    @PutMapping
    @PreAuthorize("hasAuthority('system:oauth2-client:edit')")
    @OperationLog(title = "OAuth2客户端管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Void> edit(@Valid @RequestBody ClientUpdateRequest request) {
        oAuth2ClientService.updateClient(request);
        return Result.success();
    }

    @Operation(summary = "删除客户端")
    @DeleteMapping
    @PreAuthorize("hasAuthority('system:oauth2-client:delete')")
    @OperationLog(title = "OAuth2客户端管理", businessType = OperationLog.BusinessType.DELETE)
    public Result<Void> remove(@RequestBody List<String> ids) {
        oAuth2ClientService.deleteClients(ids);
        return Result.success();
    }

    @Operation(summary = "重新生成客户端密钥")
    @PutMapping("/{id}/secret")
    @PreAuthorize("hasAuthority('system:oauth2-client:edit')")
    @OperationLog(title = "OAuth2客户端管理", businessType = OperationLog.BusinessType.UPDATE)
    public Result<Map<String, String>> regenerateSecret(@PathVariable String id) {
        String newSecret = oAuth2ClientService.regenerateSecret(id);
        return Result.success(Map.of("clientSecret", newSecret));
    }
}
