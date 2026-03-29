package com.forge.admin.modules.quartz.job;

import com.forge.admin.modules.quartz.service.JobLogService;
import com.forge.admin.modules.system.entity.SysJob;
import com.forge.admin.modules.system.service.SysJobService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * Quartz Job 执行类
 * 允许并发执行
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
            // 获取任务参数
            JobDataMap dataMap = context.getMergedJobDataMap();
            job = (SysJob) dataMap.get(JOB_PARAM_KEY);

            if (job == null) {
                log.error("任务参数为空，无法执行任务");
                return;
            }

            log.info("开始执行定时任务: {} - {}", job.getJobName(), job.getInvokeTarget());

            // 获取 ApplicationContext
            SchedulerContext schedulerContext = context.getScheduler().getContext();
            ApplicationContext applicationContext = (ApplicationContext) schedulerContext.get("applicationContextKey");

            // 执行任务
            executeInvokeTarget(applicationContext, job);

            success = true;
            log.info("定时任务执行成功: {}", job.getJobName());

        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.error("定时任务执行失败: {}", job != null ? job.getJobName() : "unknown", e);
        } finally {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            // 记录执行日志
            try {
                SchedulerContext schedulerContext = context.getScheduler().getContext();
                ApplicationContext applicationContext = (ApplicationContext) schedulerContext.get("applicationContextKey");
                JobLogService jobLogService = applicationContext.getBean(JobLogService.class);

                if (job != null) {
                    jobLogService.recordJobLog(job, startTime, duration, success, errorMessage);
                }
            } catch (Exception e) {
                log.error("记录任务执行日志失败", e);
            }
        }
    }

    /**
     * 执行调用目标
     */
    private void executeInvokeTarget(ApplicationContext applicationContext, SysJob job) throws Exception {
        String invokeTarget = job.getInvokeTarget();

        if (invokeTarget == null || invokeTarget.trim().isEmpty()) {
            throw new IllegalArgumentException("调用目标不能为空");
        }

        // 支持多种调用方式：
        // 1. bean.method(params) - 调用 Spring Bean 的方法
        // 2. class.method(params) - 调用静态方法（暂不支持）

        String target = invokeTarget.trim();

        // 解析 bean 名称和方法名
        int dotIndex = target.indexOf('.');
        if (dotIndex == -1) {
            throw new IllegalArgumentException("调用目标格式错误，应为: beanName.methodName(params)");
        }

        String beanName = target.substring(0, dotIndex);
        String methodPart = target.substring(dotIndex + 1);

        // 解析方法名和参数
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

        // 获取 Bean
        Object bean = applicationContext.getBean(beanName);

        // 解析参数
        Object[] params = parseParams(paramsStr);

        // 执行方法
        Class<?>[] paramTypes = new Class<?>[params.length];
        for (int i = 0; i < params.length; i++) {
            paramTypes[i] = params[i] != null ? params[i].getClass() : String.class;
        }

        // 尝试找到匹配的方法
        java.lang.reflect.Method method = findMethod(bean.getClass(), methodName, paramTypes);
        if (method == null) {
            throw new NoSuchMethodException("找不到方法: " + methodName);
        }

        method.setAccessible(true);
        method.invoke(bean, params);
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
        // 尝试从父类查找
        if (clazz.getSuperclass() != null) {
            return findMethod(clazz.getSuperclass(), methodName, paramTypes);
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

        // 字符串（带引号）
        if ((param.startsWith("\"") && param.endsWith("\"")) ||
            (param.startsWith("'") && param.endsWith("'"))) {
            return param.substring(1, param.length() - 1);
        }

        // 布尔值
        if ("true".equalsIgnoreCase(param)) {
            return true;
        }
        if ("false".equalsIgnoreCase(param)) {
            return false;
        }

        // null
        if ("null".equalsIgnoreCase(param)) {
            return null;
        }

        // 数字
        try {
            if (param.contains(".")) {
                return Double.parseDouble(param);
            } else {
                return Long.parseLong(param);
            }
        } catch (NumberFormatException e) {
            // 不是数字，返回字符串
            return param;
        }
    }
}
