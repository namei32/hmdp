package com.hmdp.messaging;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.event.BlogPublishedEvent;
import com.hmdp.entity.Follow;
import com.hmdp.service.IFollowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogPublishedConsumerTest {

    private BlogPublishedConsumer consumer;

    @Mock
    private IFollowService followService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @BeforeEach
    void setUp() {
        consumer = new BlogPublishedConsumer(followService, stringRedisTemplate);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    void handleBlogPublished_shouldFanOutToFollowerFeeds() {
        Follow followerOne = new Follow();
        followerOne.setUserId(11L);
        Follow followerTwo = new Follow();
        followerTwo.setUserId(12L);

        when(followService.list(any(QueryWrapper.class)))
                .thenReturn(Arrays.asList(followerOne, followerTwo));

        consumer.handleBlogPublished(new BlogPublishedEvent(101L, 9L, 1700000000000L));

        verify(zSetOperations).add("feed:11", "101", 1700000000000L);
        verify(zSetOperations).add("feed:12", "101", 1700000000000L);
    }

    @Test
    void handleBlogPublished_shouldSkipWhenNoFollowers() {
        when(followService.list(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        consumer.handleBlogPublished(new BlogPublishedEvent(101L, 9L, 1700000000000L));

        verifyNoInteractions(zSetOperations);
    }
}
