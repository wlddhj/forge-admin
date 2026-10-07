package com.forge.modules.system.job;

import java.util.Map;

/**
 * 定时任务统一接口
 * <p>所有通过 sys_job 调度的任务 Bean 必须实现本接口，否则保存任务时被拒绝、触发时抛出异常。
 * <p>调用目标格式保持 {@code beanName.methodName(params)}：接口方法 {@link #execute(Map)}
 * 与实现类中的任意 public 方法均可作为目标；结构化参数建议通过 sys_job.job_params 传入，
 * 由 {@link #execute(Map)} 接收。
 */
public interface JobHandler {

    /**
     * 执行任务
     *
     * @param params 任务参数，来自 sys_job.job_params JSON（无参数时可能为 null，实现方需判空）
     */
    void execute(Map<String, Object> params);

    /**
     * 任务展示名（用于日志），默认返回 null 表示使用 sys_job.job_name
     */
    default String getJobName() {
        return null;
    }
}
