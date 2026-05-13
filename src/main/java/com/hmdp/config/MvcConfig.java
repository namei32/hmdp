package com.hmdp.config;

import com.hmdp.ratelimit.interceptor.RateLimitInterceptor;
import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
        @Resource
        private StringRedisTemplate stringRedisTemplate;
        @Resource
        private RateLimitInterceptor rateLimitInterceptor;

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
                String[] swaggerExcludes = new String[] {
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/webjars/**",
                                "/error"
                };

                registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                                .excludePathPatterns(
                                                "/user/code",
                                                "/user/login")
                                .excludePathPatterns(swaggerExcludes)
                                .order(0);

                registry.addInterceptor(rateLimitInterceptor)
                                .excludePathPatterns(swaggerExcludes)
                                .order(1);

                registry.addInterceptor(new LoginInterceptor())
                                .excludePathPatterns(
                                                "/shop/**",
                                                "/shop-type/**",
                                                "/blog/hot",
                                                "/user/code",
                                                "/user/login",
                                                "/voucher/**",
                                                "/upload/**",
                                                "/api/cs/**",
                                                "/customer-service.html",
                                                "/css/**",
                                                "/js/**")
                                .excludePathPatterns(swaggerExcludes)
                                .order(2);

        }
}
