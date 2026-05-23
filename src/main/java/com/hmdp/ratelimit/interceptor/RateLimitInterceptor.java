package com.hmdp.ratelimit.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.ratelimit.annotation.RateLimit;
import com.hmdp.ratelimit.enums.RateLimitTarget;
import com.hmdp.ratelimit.model.RateLimitDecision;
import com.hmdp.ratelimit.service.RateLimitEngine;
import com.hmdp.ratelimit.util.ClientIpUtils;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RateLimitEngine rateLimitEngine;

    public RateLimitInterceptor(RateLimitEngine rateLimitEngine) {
        this.rateLimitEngine = rateLimitEngine;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            rateLimit = handlerMethod.getBeanType().getAnnotation(RateLimit.class);
        }
        if (rateLimit == null) {
            return true;
        }

        String identifier = resolveIdentifier(request, rateLimit.target());
        RateLimitDecision decision = rateLimitEngine.check(identifier, rateLimit);
        if (decision.isAllowed()) {
            return true;
        }

        response.setStatus(429);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(decision.getMessage())));
        return false;
    }

    private String resolveIdentifier(HttpServletRequest request, RateLimitTarget target) {
        if (target == RateLimitTarget.IP) {
            return ClientIpUtils.resolveClientIp(request);
        }
        UserDTO user = UserHolder.getUser();
        if (target == RateLimitTarget.USER) {
            return user == null ? "anonymous" : "user:" + user.getId();
        }
        if (user != null) {
            return "user:" + user.getId();
        }
        return "ip:" + ClientIpUtils.resolveClientIp(request);
    }
}
