package com.hmdp.messaging;

import com.hmdp.event.BlogLikedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BlogLikedConsumer {

    @KafkaListener(topics = KafkaTopics.BLOG_LIKED, groupId = "hmdp-like")
    public void handleBlogLiked(BlogLikedEvent event) {
        log.debug("received blog liked event: blogId={}, userId={}, liked={}",
                event.getBlogId(), event.getUserId(), event.getAction());
    }
}
