package com.hmdp.messaging;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.event.BlogPublishedEvent;
import com.hmdp.entity.Follow;
import com.hmdp.service.IFollowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BlogPublishedConsumer {

    private static final String FEED_KEY_PREFIX = "feed:";

    private final IFollowService followService;
    private final StringRedisTemplate stringRedisTemplate;

    @KafkaListener(topics = KafkaTopics.BLOG_PUBLISHED, groupId = "hmdp-feed")
    public void handleBlogPublished(BlogPublishedEvent event) {
        List<Follow> followers = followService.list(
                new QueryWrapper<Follow>().eq("follow_user_id", event.getAuthorId()));
        if (followers == null || followers.isEmpty()) {
            log.debug("no followers for authorId={}, skip feed fan-out", event.getAuthorId());
            return;
        }

        long publishedAt = event.getPublishedAt() == null ? System.currentTimeMillis() : event.getPublishedAt();
        for (Follow follower : followers) {
            stringRedisTemplate.opsForZSet().add(
                    FEED_KEY_PREFIX + follower.getUserId(),
                    event.getBlogId().toString(),
                    publishedAt);
        }
        log.info("distributed blogId={} to {} follower feeds", event.getBlogId(), followers.size());
    }
}
