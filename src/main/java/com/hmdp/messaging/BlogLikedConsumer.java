package com.hmdp.messaging;

import com.hmdp.event.BlogLikedEvent;
import com.hmdp.service.IBlogService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
@Component
public class BlogLikedConsumer {
    private static final String LIKED_KEY_PREFIX = "blog:liked:";
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IBlogService blogService;

    @KafkaListener(topics = KafkaTopics.BLOG_LIKED, groupId = "hmdp-like")
    private void handleBlogLiked(BlogLikedEvent event) {
        String key = LIKED_KEY_PREFIX + event.getBlogId();
        if (event.getAction() == true) {

        } else {

        }
    }

}
