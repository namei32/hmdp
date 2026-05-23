package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("authorization");

        if (!StringUtils.hasText(token)) {
            return true;
        }
//        UserDTO user = (UserDTO) session.getAttribute("user");
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        if (map.isEmpty()) {
            return true;
        }
        UserDTO userDto = BeanUtil.fillBeanWithMap(map, new UserDTO(), false);
        UserHolder.saveUser(userDto);

        stringRedisTemplate.expire(LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws Exception {
        UserHolder.removeUser();
    }
}

