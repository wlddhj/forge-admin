package com.forge.modules.workflow.controller.admin;

import com.forge.common.response.Result;
import com.forge.modules.workflow.framework.trigger.TriggerBeanRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 触发器 Bean 管理 API
 *
 * 供流程设计器下拉选择使用,仅暴露带 @FlowLongTrigger 注解的 Bean。
 */
@Tag(name = "触发器 Bean")
@RestController
@RequestMapping("/workflow/trigger")
@RequiredArgsConstructor
public class WfTriggerController {

    private final TriggerBeanRegistry triggerBeanRegistry;

    @Operation(summary = "列出所有可用的触发器 Bean")
    @GetMapping("/beans")
    public Result<List<TriggerBeanRegistry.TriggerBeanInfo>> listBeans() {
        return Result.success(triggerBeanRegistry.list());
    }
}
