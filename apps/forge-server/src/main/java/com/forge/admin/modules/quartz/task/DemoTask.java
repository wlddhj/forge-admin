package com.forge.admin.modules.quartz.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 示例任务
 * 可通过 invokeTarget: demoTask.execute("参数") 调用
 */
@Slf4j
@Component("demoTask")
public class DemoTask {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 执行任务
     * @param message 消息参数
     */
    public void execute(String message) {
        log.info("========== 定时任务执行 ==========");
        log.info("执行时间: {}", LocalDateTime.now().format(FORMATTER));
        log.info("任务消息: {}", message);
        log.info("=================================");
    }

    /**
     * 清理过期数据
     */
    public void cleanExpiredData() {
        log.info("========== 清理过期数据 ==========");
        log.info("执行时间: {}", LocalDateTime.now().format(FORMATTER));
        // 这里可以添加实际的数据清理逻辑
        log.info("清理完成");
        log.info("=================================");
    }

    /**
     * 无参方法示例
     */
    public void noParams() {
        log.info("执行无参任务: {}", LocalDateTime.now().format(FORMATTER));
    }

    /**
     * 多参数示例
     */
    public void multiParams(String param1, Integer param2, Boolean param3) {
        log.info("执行多参数任务: param1={}, param2={}, param3={}", param1, param2, param3);
    }
}
