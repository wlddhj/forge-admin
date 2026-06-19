package com.forge.framework.web.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.web.util.HtmlUtils;

import java.util.regex.Pattern;

/**
 * XSS 安全 HttpServletRequest 包装器
 * 对请求参数进行 HTML 转义处理
 *
 * @author standadmin
 */
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

    /**
     * 危险字符正则（script, eval, expression 等）
     */
    private static final Pattern DANGER_PATTERN = Pattern.compile(
            "<script.*?>.*?</script>|javascript:|onerror=|onclick=|onload=|eval\\(|expression\\(",
            Pattern.CASE_INSENSITIVE
    );

    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public String getParameter(String name) {
        String value = super.getParameter(name);
        return sanitize(value);
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) {
            return null;
        }
        String[] sanitized = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            sanitized[i] = sanitize(values[i]);
        }
        return sanitized;
    }

    @Override
    public String getHeader(String name) {
        String value = super.getHeader(name);
        return sanitize(value);
    }

    /**
     * XSS 清理
     */
    private String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        // 先移除危险脚本
        value = DANGER_PATTERN.matcher(value).replaceAll("");
        // HTML 转义
        return HtmlUtils.htmlEscape(value);
    }
}