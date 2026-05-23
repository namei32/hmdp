package com.hmdp.config;

import com.hmdp.ratelimit.interceptor.RateLimitInterceptor;
import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    private static final String[] SWAGGER_EXCLUDES = {
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/swagger-resources/**",
            "/webjars/**",
            "/error"
    };

    private static final String[] STATIC_EXCLUDES = {
            "/",
            "/index.html",
            "/customer-service.html",
            "/css/**",
            "/js/**"
    };

    private static final String[] LOGIN_EXCLUDES = {
            "/shop/**",
            "/shop-type/**",
            "/blog/hot",
            "/user/code",
            "/user/login",
            "/voucher/**",
            "/upload/**",
            "/api/cs/**",
            "/cs/**"
    };

    private final StringRedisTemplate stringRedisTemplate;
    private final RateLimitInterceptor rateLimitInterceptor;

    public MvcConfig(StringRedisTemplate stringRedisTemplate, RateLimitInterceptor rateLimitInterceptor) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/index.html");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .excludePathPatterns("/user/code", "/user/login")
                .excludePathPatterns(SWAGGER_EXCLUDES)
                .excludePathPatterns(STATIC_EXCLUDES)
                .order(0);

        registry.addInterceptor(rateLimitInterceptor)
                .excludePathPatterns(SWAGGER_EXCLUDES)
                .excludePathPatterns(STATIC_EXCLUDES)
                .order(1);

        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(LOGIN_EXCLUDES)
                .excludePathPatterns(SWAGGER_EXCLUDES)
                .excludePathPatterns(STATIC_EXCLUDES)
                .order(2);
    }
}
