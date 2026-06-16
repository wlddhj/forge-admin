package com.forge.modules.system.dto.app;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

/**
 * 账号注销请求
 *
 * @author forge
 */
@Data
public class DeactivateRequest {
    /**
     * 是否确认注销
     */
    @AssertTrue(message = "必须确认注销")
    private Boolean confirm;
}