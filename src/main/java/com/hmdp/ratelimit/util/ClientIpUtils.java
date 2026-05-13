package com.hmdp.ratelimit.util;

import javax.servlet.http.HttpServletRequest;

public final class ClientIpUtils {

    private ClientIpUtils() {
    }

    public static String resolveClientIp(HttpServletRequest request) {
        String[] headers = { "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP" };
        for (String header : headers) {
            String value = request.getHeader(header);
            if (isValid(value)) {
                int commaIndex = value.indexOf(',');
                return commaIndex > 0 ? value.substring(0, commaIndex).trim() : value.trim();
            }
        }
        return request.getRemoteAddr();
    }

    private static boolean isValid(String value) {
        return value != null && value.length() > 0 && !"unknown".equalsIgnoreCase(value);
    }
}
