package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    private static final String FOLLOWS_KEY_PREFIX = "follows:";

    private final StringRedisTemplate stringRedisTemplate;
    private final IUserService userService;

    public FollowServiceImpl(StringRedisTemplate stringRedisTemplate, IUserService userService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.userService = userService;
    }

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = FOLLOWS_KEY_PREFIX + userId;
        if (Boolean.TRUE.equals(isFollow)) {
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);

            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollowed(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Long count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);

    }

    @Override
    public Result followCommons(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = FOLLOWS_KEY_PREFIX + userId;
        String targetKey = FOLLOWS_KEY_PREFIX + id;
        Set<String> set = stringRedisTemplate.opsForSet().intersect(key, targetKey);
        if (set == null || set.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = set.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOS = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}
