package com.forge.modules.workflow.dto.instance;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 手动抄送请求（实例级）
 *
 * @author forge-admin
 */
@Data
public class InstanceCopyRequest {

    @NotEmpty(message = "抄送用户列表不能为空")
    private List<Long> copyUserIds;

    /** 抄送原因 */
    private String reason;
}
