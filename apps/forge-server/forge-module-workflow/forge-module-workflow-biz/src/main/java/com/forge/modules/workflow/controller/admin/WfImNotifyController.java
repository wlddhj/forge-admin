package com.forge.modules.workflow.controller.admin;

import com.forge.common.response.Result;
import com.forge.common.utils.UserContext;
import com.forge.modules.workflow.im.ImChannelConfigService;
import com.forge.modules.workflow.im.ImConfigReader;
import com.forge.modules.workflow.im.ImNotificationChannel;
import com.forge.modules.workflow.im.ImNotificationMessage;
import com.forge.modules.workflow.im.ImSendResult;
import com.forge.modules.workflow.im.ImUserBindingResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * IM 工作通知联调端点：向指定用户（默认当前登录人）的已绑定渠道发送测试消息，
 * 用于凭据与绑定自检（Swagger/curl），不做前端入口。租户缺省取上下文（非租户模式=租户1）。
 *
 * @author forge-admin
 */
@Tag(name = "IM通知管理")
@RestController
@RequestMapping("/workflow/im")
@RequiredArgsConstructor
public class WfImNotifyController {

    private final List<ImNotificationChannel> channels;
    private final ImUserBindingResolver bindingResolver;
    private final ImConfigReader configReader;
    private final ImChannelConfigService configService;

    public record ImTestRequest(String channel, Long userId, Long tenantId) {
    }

    public record ImTestResult(String channel, boolean enabled, boolean bound, boolean sent, String error) {
    }

    @Operation(summary = "发送测试通知（校验凭据与绑定）")
    @PostMapping("/test")
    @PreAuthorize("hasAuthority('workflow:im:test')")
    public Result<List<ImTestResult>> test(@RequestBody ImTestRequest request) {
        Long userId = request.userId() != null ? request.userId() : UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.failed("未登录且未指定 userId");
        }
        Long tenantId = request.tenantId() != null ? request.tenantId() : configService.resolveTenantId();
        String baseUrl = configReader.webBaseUrl();
        ImNotificationMessage message = new ImNotificationMessage("test",
                "审批IM通知测试", "这是一条测试消息，收到即表示凭据与绑定配置正确。",
                baseUrl + "/workflow/task/todo", null, null, null);

        List<ImTestResult> results = new ArrayList<>();
        for (ImNotificationChannel channel : channels) {
            if (StringUtils.hasText(request.channel()) && !channel.channel().equals(request.channel())) {
                continue;
            }
            boolean enabled = channel.enabled(tenantId);
            boolean sent = false;
            boolean bound = false;
            String error = null;
            if (enabled) {
                var bindings = bindingResolver.resolve(channel.channel(), tenantId, List.of(userId));
                String corpUserid = bindings.get(userId);
                bound = corpUserid != null;
                if (bound) {
                    ImSendResult sendResult = channel.sendToUser(tenantId, corpUserid, message);
                    sent = sendResult.success();
                    error = sendResult.error();
                }
            }
            results.add(new ImTestResult(channel.channel(), enabled, bound, sent, error));
        }
        return Result.success(results);
    }
}
