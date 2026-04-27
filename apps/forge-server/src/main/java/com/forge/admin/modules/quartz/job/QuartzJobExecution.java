package com.forge.admin.modules.quartz.job;

import com.forge.admin.modules.quartz.service.JobLogService;
import com.forge.admin.modules.quartz.service.JobNotifyService;
import com.forge.admin.modules.system.entity.SysJob;
import com.forge.admin.modules.system.service.SysJobService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Quartz Job 执行类
 * 支持超时控制、失败重试、执行统计
 */
@Slf4j
@DisallowConcurrentExecution
public class QuartzJobExecution implements Job {

    private static final String JOB_PARAM_KEY = "JOB_PARAM_KEY";

    /**
     * 设置任务参数
     */
    public static JobDataMap createJobDataMap(SysJob job) {
        JobDataMap dataMap = new JobDataMap();
        dataMap.put(JOB_PARAM_KEY, job);
        return dataMap;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        SysJob job = null;
        long startTime = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;

        try {
            JobDataMap dataMap = context.getMergedJobDataMap();
            job = (SysJob) dataMap.get(JOB_PARAM_KEY);

            if (job == null) {
                log.error("任务参数为空，无法执行任务");
                return;
            }

            log.info("开始执行定时任务: {} - {}", job.getJobName(), job.getInvokeTarget());

            SchedulerContext schedulerContext = context.getScheduler().getContext();
            ApplicationContext applicationContext = (ApplicationContext) schedulerContext.get("applicationContextKey");

            // 带超时和重试执行
            success = executeWithRetryAndTimeout(applicationContext, job);

            if (success) {
                log.info("定时任务执行成功: {}", job.getJobName());
            }

        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.error("定时任务执行失败: {}", job != null ? job.getJobName() : "unknown", e);
        } finally {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            // 记录执行日志 + 更新任务统计 + 发送通知
            try {
                SchedulerContext schedulerContext = context.getScheduler().getContext();
                ApplicationContext applicationContext = (ApplicationContext) schedulerContext.get("applicationContextKey");
                JobLogService jobLogService = applicationContext.getBean(JobLogService.class);
                JobNotifyService jobNotifyService = applicationContext.getBean(JobNotifyService.class);

                if (job != null) {
                    String status = success ? "SUCCESS" : "FAIL";
                    // 如果超时但没显式标记
                    if (!success && errorMessage != null && errorMessage.contains("Timeout")) {
                        status = "TIMEOUT";
                    }
                    if (!success && errorMessage == null) {
                        errorMessage = "执行失败";
                    }
                    jobLogService.recordJobLog(job, startTime, duration, success, errorMessage);

                    // 更新 sys_job 执行统计
                    updateJobStats(applicationContext, job.getId(), status, duration);

                    // 发送通知
                    jobNotifyService.notify(job, success, duration, errorMessage);
                }
            } catch (Exception e) {
                log.error("记录任务执行日志失败", e);
            }
        }
    }

    /**
     * 带重试和超时的任务执行
     */
    private boolean executeWithRetryAndTimeout(ApplicationContext applicationContext, SysJob job) {
        int retryCount = job.getRetryCount() != null ? job.getRetryCount() : 0;
        int retryInterval = job.getRetryInterval() != null ? job.getRetryInterval() : 60;
        int timeout = job.getTimeout() != null ? job.getTimeout() : 0;

        int maxAttempts = 1 + retryCount; // 首次执行 + 重试次数

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                if (timeout > 0) {
                    // 带超时执行
                    executeWithTimeout(applicationContext, job, timeout);
                } else {
                    // 无超时限制
                    executeInvokeTarget(applicationContext, job);
                }
                return true;
            } catch (TimeoutException e) {
                log.warn("任务执行超时: {} (attempt {}/{}, timeout={}s)",
                        job.getJobName(), attempt, maxAttempts, timeout);
                if (attempt >= maxAttempts) {
                    return false;
                }
            } catch (Exception e) {
                log.warn("任务执行失败: {} (attempt {}/{}): {}",
                        job.getJobName(), attempt, maxAttempts, e.getMessage());
                if (attempt >= maxAttempts) {
                    return false;
                }
            }

            // 重试间隔等待
            if (attempt < maxAttempts) {
                try {
                    log.info("等待 {}s 后重试任务: {}", retryInterval, job.getJobName());
                    Thread.sleep(retryInterval * 1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * 带超时的执行
     */
    private void executeWithTimeout(ApplicationContext applicationContext, SysJob job, int timeoutSeconds)
            throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = executor.submit(() -> {
                try {
                    executeInvokeTarget(applicationContext, job);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            executor.shutdownNow();
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re && re.getCause() != null) {
                throw (Exception) re.getCause();
            }
            throw e;
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 执行调用目标
     * <p>支持两种参数传递方式：
     * <ul>
     *   <li>inline 参数：invokeTarget 中声明的参数，如 beanName.method("arg")</li>
     *   <li>jobParams 参数：sys_job.job_params JSON 字段，传递 Map 给接受 Map 参数的重载方法</li>
     * </ul>
     * <p>如果 jobParams 非空且目标方法有接受 Map 参数的重载，优先使用 jobParams 调用。
     */
    @SuppressWarnings("unchecked")
    private void executeInvokeTarget(ApplicationContext applicationContext, SysJob job) throws Exception {
        String invokeTarget = job.getInvokeTarget();

        if (invokeTarget == null || invokeTarget.trim().isEmpty()) {
            throw new IllegalArgumentException("调用目标不能为空");
        }

        String target = invokeTarget.trim();

        int dotIndex = target.indexOf('.');
        if (dotIndex == -1) {
            throw new IllegalArgumentException("调用目标格式错误，应为: beanName.methodName(params)");
        }

        String beanName = target.substring(0, dotIndex);
        String methodPart = target.substring(dotIndex + 1);

        int parenIndex = methodPart.indexOf('(');
        String methodName;
        String paramsStr = null;

        if (parenIndex != -1) {
            methodName = methodPart.substring(0, parenIndex);
            int closeParen = methodPart.lastIndexOf(')');
            if (closeParen != -1) {
                paramsStr = methodPart.substring(parenIndex + 1, closeParen);
            }
        } else {
            methodName = methodPart;
        }

        Object bean = applicationContext.getBean(beanName);
        Map<String, Object> jobParams = job.getJobParams();

        // 优先尝试：如果 jobParams 非空，查找接受 Map<String, Object> 参数的方法
        if (jobParams != null && !jobParams.isEmpty()) {
            java.lang.reflect.Method mapMethod = findMethodByParamCount(bean.getClass(), methodName, 1);
            if (mapMethod != null && Map.class.isAssignableFrom(mapMethod.getParameterTypes()[0])) {
                log.info("使用 jobParams 调用任务: {}.{}, params={}", beanName, methodName, jobParams.keySet());
                mapMethod.setAccessible(true);
                mapMethod.invoke(bean, jobParams);
                return;
            }
        }

        // 回退到 inline 参数方式
        Object[] params = parseParams(paramsStr);

        Class<?>[] paramTypes = new Class<?>[params.length];
        for (int i = 0; i < params.length; i++) {
            paramTypes[i] = params[i] != null ? params[i].getClass() : String.class;
        }

        java.lang.reflect.Method method = findMethod(bean.getClass(), methodName, paramTypes);
        if (method == null) {
            throw new NoSuchMethodException("找不到方法: " + methodName);
        }

        method.setAccessible(true);
        method.invoke(bean, params);
    }

    /**
     * 更新任务执行统计
     */
    private void updateJobStats(ApplicationContext applicationContext, Long jobId, String status, long duration) {
        try {
            SysJobService sysJobService = applicationContext.getBean(SysJobService.class);
            SysJob updateJob = new SysJob();
            updateJob.setId(jobId);
            updateJob.setLastExecuteAt(LocalDateTime.now());
            updateJob.setLastExecuteStatus(status);
            updateJob.setLastExecuteDuration(duration);

            // 获取当前值并递增
            SysJob current = sysJobService.getById(jobId);
            if (current != null) {
                int totalCount = (current.getTotalExecuteCount() != null ? current.getTotalExecuteCount() : 0) + 1;
                updateJob.setTotalExecuteCount(totalCount);

                if ("SUCCESS".equals(status)) {
                    updateJob.setSuccessCount((current.getSuccessCount() != null ? current.getSuccessCount() : 0) + 1);
                    updateJob.setFailureCount(current.getFailureCount() != null ? current.getFailureCount() : 0);
                } else {
                    updateJob.setFailureCount((current.getFailureCount() != null ? current.getFailureCount() : 0) + 1);
                    updateJob.setSuccessCount(current.getSuccessCount() != null ? current.getSuccessCount() : 0);
                }
            }

            sysJobService.updateById(updateJob);
        } catch (Exception e) {
            log.error("更新任务执行统计失败: jobId={}", jobId, e);
        }
    }

    /**
     * 查找匹配的方法
     */
    private java.lang.reflect.Method findMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
        java.lang.reflect.Method[] methods = clazz.getDeclaredMethods();
        for (java.lang.reflect.Method method : methods) {
            if (method.getName().equals(methodName) && method.getParameterCount() == paramTypes.length) {
                return method;
            }
        }
        if (clazz.getSuperclass() != null) {
            return findMethod(clazz.getSuperclass(), methodName, paramTypes);
        }
        return null;
    }

    /**
     * 按方法名和参数数量查找方法
     */
    private java.lang.reflect.Method findMethodByParamCount(Class<?> clazz, String methodName, int paramCount) {
        java.lang.reflect.Method[] methods = clazz.getDeclaredMethods();
        for (java.lang.reflect.Method method : methods) {
            if (method.getName().equals(methodName) && method.getParameterCount() == paramCount) {
                return method;
            }
        }
        if (clazz.getSuperclass() != null) {
            return findMethodByParamCount(clazz.getSuperclass(), methodName, paramCount);
        }
        return null;
    }

    /**
     * 解析参数
     */
    private Object[] parseParams(String paramsStr) {
        if (paramsStr == null || paramsStr.trim().isEmpty()) {
            return new Object[0];
        }

        String[] paramArray = paramsStr.split(",");
        Object[] params = new Object[paramArray.length];

        for (int i = 0; i < paramArray.length; i++) {
            String param = paramArray[i].trim();
            params[i] = parseParam(param);
        }

        return params;
    }

    /**
     * 解析单个参数
     */
    private Object parseParam(String param) {
        if (param.isEmpty()) {
            return "";
        }

        if ((param.startsWith("\"") && param.endsWith("\"")) ||
            (param.startsWith("'") && param.endsWith("'"))) {
            return param.substring(1, param.length() - 1);
        }

        if ("true".equalsIgnoreCase(param)) {
            return true;
        }
        if ("false".equalsIgnoreCase(param)) {
            return false;
        }

        if ("null".equalsIgnoreCase(param)) {
            return null;
        }

        try {
            if (param.contains(".")) {
                return Double.parseDouble(param);
            } else {
                return Long.parseLong(param);
            }
        } catch (NumberFormatException e) {
            return param;
        }
    }
}
