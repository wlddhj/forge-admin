package com.forge.modules.workflow.framework;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 审批操作类型枚举
 */
@Getter
@AllArgsConstructor
public enum ApprovalActionTypeEnum {

    SUBMIT("submit", "提交"),
    APPROVE("approve", "通过"),
    REJECT("reject", "驳回"),
    DELEGATE("delegate", "委派"),
    TRANSFER("transfer", "转办"),
    RETURN("return", "退回"),
    WITHDRAW("withdraw", "撤回"),
    COPY("copy", "抄送"),
    CLAIM("claim", "认领"),
    CANCEL("cancel", "取消"),
    SIGN_CREATE("sign_create", "加签"),
    SIGN_DELETE("sign_delete", "减签");

    private final String code;
    private final String description;

    public static ApprovalActionTypeEnum getByCode(String code) {
        if (code == null) return null;
        for (ApprovalActionTypeEnum action : values()) {
            if (action.getCode().equals(code)) {
                return action;
            }
        }
        return null;
    }
}
