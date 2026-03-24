package com.forge.admin.common.aspect;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.forge.admin.common.annotation.OperationLog;
import com.forge.admin.common.utils.SecurityUtils;
import com.forge.admin.common.utils.UserContext;
import com.forge.admin.modules.system.entity.SysOperationLog;
import com.forge.admin.modules.system.service.SysOperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 操作日志切面
 *
 * @author standadmin
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final SysOperationLogService operationLogService;

    /**
     * 配置切入点
     */
    @Pointcut("@annotation(operationLog)")
    public void logPointCut(OperationLog operationLog) {
    }

    /**
     * 处理完请求后执行
     */
    @AfterReturning(pointcut = "logPointCut(operationLog)", returning = "jsonResult")
    public void doAfterReturning(JoinPoint joinPoint, OperationLog operationLog, Object jsonResult) {
        handleLog(joinPoint, operationLog, null, jsonResult);
    }

    /**
     * 拦截异常操作
     */
    @AfterThrowing(pointcut = "logPointCut(operationLog)", throwing = "e")
    public void doAfterThrowing(JoinPoint joinPoint, OperationLog operationLog, Exception e) {
        handleLog(joinPoint, operationLog, e, null);
    }

    /**
     * 处理日志
     */
    protected void handleLog(JoinPoint joinPoint, OperationLog operationLogAnnotation, Exception e, Object jsonResult) {
        try {
            // 获取当前请求
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return;
            }
            HttpServletRequest request = attributes.getRequest();

            // 构建日志对象
            SysOperationLog operationLog = new SysOperationLog();
            operationLog.setStatus(1);
            operationLog.setOperateTime(LocalDateTime.now());

            // 异常信息
            if (e != null) {
                operationLog.setStatus(0);
                operationLog.setErrorMsg(e.getMessage());
            }

            // 标题和业务类型
            operationLog.setTitle(operationLogAnnotation.title());
            operationLog.setBusinessType(operationLogAnnotation.businessType().name());

            // 请求信息
            operationLog.setRequestMethod(request.getMethod());
            operationLog.setRequestUrl(request.getRequestURI());

            // 操作人信息
            String username = SecurityUtils.getCurrentUsername();
            operationLog.setOperatorName(username);
            UserContext userContext = UserContext.get();
            if (userContext != null) {
                operationLog.setOperatorId(userContext.getUserId());
                operationLog.setDeptName(userContext.getDeptName());
            }

            // IP 信息
            String ip = getIpAddress(request);
            operationLog.setOperateIp(ip);

            // 请求参数
            if (operationLogAnnotation.isSaveRequestData()) {
                String params = getRequestParams(joinPoint);
                operationLog.setRequestParam(params);
            }

            // 响应结果
            if (operationLogAnnotation.isSaveResponseData() && jsonResult != null) {
                operationLog.setJsonResult(JSONUtil.toJsonStr(jsonResult));
            }

            // 异步保存日志
            operationLogService.save(operationLog);
        } catch (Exception ex) {
            log.error("保存操作日志异常", ex);
        }
    }

    /**
     * 获取请求参数
     */
    private String getRequestParams(JoinPoint joinPoint) {
        Map<String, Object> params = new HashMap<>();
        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0) {
            for (Object arg : args) {
                if (arg instanceof HttpServletRequest
                        || arg instanceof HttpServletResponse
                        || arg instanceof MultipartFile
                        || arg instanceof BindingResult) {
                    continue;
                }
                try {
                    params.put("param", arg);
                } catch (Exception e) {
                    log.warn("序列化请求参数异常: {}", e.getMessage());
                }
            }
        }
        return JSONUtil.toJsonStr(params);
    }

    /**
     * 获取客户端 IP
     */
    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StrUtil.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (StrUtil.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (StrUtil.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (StrUtil.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (StrUtil.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个
        if (StrUtil.isNotEmpty(ip) && ip.length() > 15 && ip.contains(",")) {
            ip = ip.substring(0, ip.indexOf(","));
        }
        return ip;
    }
}
