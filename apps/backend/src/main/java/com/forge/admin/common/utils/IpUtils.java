package com.forge.admin.common.utils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * IP地址工具类
 */
@Slf4j
public class IpUtils {

    private static final String UNKNOWN = "unknown";
    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final String LOCALHOST_IPV6 = "0:0:0:0:0:0:0:1";

    /**
     * 获取客户端真实IP地址
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }

        String ip = request.getHeader("X-Forwarded-For");
        if (isEmptyIp(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (isEmptyIp(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (isEmptyIp(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (isEmptyIp(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (isEmptyIp(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (isEmptyIp(ip)) {
            ip = request.getRemoteAddr();
            // 对于通过多个代理的情况，第一个IP才是客户端真实IP
            if (LOCALHOST_IP.equals(ip) || LOCALHOST_IPV6.equals(ip)) {
                // 根据网卡取本机配置的IP
                try {
                    ip = java.net.InetAddress.getLocalHost().getHostAddress();
                } catch (Exception e) {
                    log.error("获取本机IP失败", e);
                }
            }
        }

        // 对于通过多个代理的情况，第一个IP才是客户端真实IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }

    /**
     * 判断IP是否为空
     */
    private static boolean isEmptyIp(String ip) {
        return ip == null || ip.isEmpty() || UNKNOWN.equalsIgnoreCase(ip);
    }

    /**
     * 根据IP获取地理位置（简化版本，实际可对接IP地址库）
     */
    public static String getLocationByIp(String ip) {
        if (ip == null || ip.isEmpty() || UNKNOWN.equalsIgnoreCase(ip)) {
            return "未知";
        }
        if (LOCALHOST_IP.equals(ip) || LOCALHOST_IPV6.equals(ip)) {
            return "内网IP";
        }
        // 简化处理，实际可以对接淘宝IP地址库、百度IP定位等服务
        return "未知";
    }

    /**
     * 从User-Agent中获取浏览器信息
     */
    public static String getBrowser(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "未知";
        }

        userAgent = userAgent.toLowerCase();

        if (userAgent.contains("edge")) {
            return "Edge";
        } else if (userAgent.contains("chrome")) {
            return "Chrome";
        } else if (userAgent.contains("firefox")) {
            return "Firefox";
        } else if (userAgent.contains("safari")) {
            return "Safari";
        } else if (userAgent.contains("opera") || userAgent.contains("opr")) {
            return "Opera";
        } else if (userAgent.contains("msie") || userAgent.contains("trident")) {
            return "IE";
        }

        return "未知";
    }

    /**
     * 从User-Agent中获取操作系统信息
     */
    public static String getOs(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "未知";
        }

        userAgent = userAgent.toLowerCase();

        if (userAgent.contains("windows")) {
            if (userAgent.contains("windows nt 10")) {
                return "Windows 10";
            } else if (userAgent.contains("windows nt 6.1")) {
                return "Windows 7";
            } else if (userAgent.contains("windows nt 6.0")) {
                return "Windows Vista";
            }
            return "Windows";
        } else if (userAgent.contains("mac")) {
            if (userAgent.contains("iphone")) {
                return "iPhone";
            } else if (userAgent.contains("ipad")) {
                return "iPad";
            }
            return "Mac OS";
        } else if (userAgent.contains("android")) {
            return "Android";
        } else if (userAgent.contains("linux")) {
            return "Linux";
        } else if (userAgent.contains("unix")) {
            return "Unix";
        }

        return "未知";
    }
}
