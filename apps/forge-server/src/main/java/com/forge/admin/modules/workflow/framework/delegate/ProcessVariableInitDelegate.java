package com.forge.admin.modules.workflow.framework.delegate;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 流程变量初始化委托
 * 流程启动后根据业务参数自动计算审批截止时间、提醒时间等
 *
 * BPMN 配置: flowable:delegateExpression="${processVariableInitDelegate}"
 * 使用场景: 开始事件之后的第一个节点，自动计算审批时效
 *
 * 流程变量:
 *   输入: priority (1=特急, 2=加急, 3=普通, 默认3)
 *   输出: deadline (截止时间), priorityLevel (优先级名称), remindTime (提醒时间)
 */
@Slf4j
@Component("processVariableInitDelegate")
public class ProcessVariableInitDelegate implements JavaDelegate {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void execute(DelegateExecution execution) {
        // 读取优先级，默认普通
        Object priorityObj = execution.getVariable("priority");
        int priority = 3;
        if (priorityObj != null) {
            priority = Integer.parseInt(priorityObj.toString());
        }

        // 根据优先级计算时效
        LocalDateTime now = LocalDateTime.now();
        String priorityLevel;
        LocalDateTime deadline;
        LocalDateTime remindTime;

        switch (priority) {
            case 1:
                priorityLevel = "特急";
                deadline = now.plusHours(4);
                remindTime = now.plusHours(2);
                break;
            case 2:
                priorityLevel = "加急";
                deadline = now.plusDays(1);
                remindTime = now.plusHours(12);
                break;
            default:
                priorityLevel = "普通";
                deadline = now.plusDays(3);
                remindTime = now.plusDays(2);
                break;
        }

        // 设置输出变量
        execution.setVariable("priorityLevel", priorityLevel);
        execution.setVariable("deadline", FORMATTER.format(deadline));
        execution.setVariable("remindTime", FORMATTER.format(remindTime));

        log.info("流程变量初始化完成: priority={}, level={}, deadline={}", priority, priorityLevel, FORMATTER.format(deadline));
    }
}
