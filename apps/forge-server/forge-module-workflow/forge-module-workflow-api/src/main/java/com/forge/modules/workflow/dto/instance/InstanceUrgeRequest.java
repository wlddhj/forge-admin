package com.forge.modules.workflow.dto.instance;

import lombok.Data;

/**
 * 流程催办请求（实例级，催全部活动审批任务）
 *
 * @author forge-admin
 */
@Data
public class InstanceUrgeRequest {

    /** 催办留言（随通知发送给审批人） */
    private String comment;
}
