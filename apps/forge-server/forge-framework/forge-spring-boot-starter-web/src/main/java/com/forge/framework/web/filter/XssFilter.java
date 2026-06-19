package com.forge.framework.web.filter;

import com.forge.framework.web.annotation.XssIgnore;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;

/**
 * XSS 过滤器
 * 对请求参数进行 XSS 清理，支持 @XssIgnore 跳过
 *
 * @author standadmin
 */
public class XssFilter implements Filter {

    private RequestMappingHandlerMapping handlerMapping;

    public void setHandlerMapping(RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // 检查是否需要跳过 XSS 过滤
        if (shouldIgnore(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        // 使用 XSS 包装器
        XssHttpServletRequestWrapper wrappedRequest = new XssHttpServletRequestWrapper(httpRequest);
        chain.doFilter(wrappedRequest, response);
    }

    /**
     * 检查是否标注了 @XssIgnore
     */
    private boolean shouldIgnore(HttpServletRequest request) {
        if (handlerMapping == null) {
            return false;
        }

        try {
            String lookupPath = UrlPathHelper.defaultInstance.getLookupPathForRequest(request);
            HandlerExecutionChain chain = handlerMapping.getHandler(request);
            if (chain == null || chain.getHandler() == null) {
                return false;
            }

            Object handler = chain.getHandler();
            if (handler instanceof HandlerMethod handlerMethod) {
                // 方法级别注解
                XssIgnore methodAnnotation = handlerMethod.getMethodAnnotation(XssIgnore.class);
                if (methodAnnotation != null) {
                    return true;
                }
                // 类级别注解
                XssIgnore classAnnotation = handlerMethod.getBeanType().getAnnotation(XssIgnore.class);
                return classAnnotation != null;
            }
        } catch (Exception e) {
            // 获取 handler 失败时继续过滤
        }
        return false;
    }
}