package com.hmdp.messaging;

import com.hmdp.event.BlogPublishedEvent;
import com.hmdp.service.MessageOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BlogEventProducer {

    private final MessageOutboxService messageOutboxService;

    public void enqueue(BlogPublishedEvent event) {
        messageOutboxService.enqueue(
                KafkaTopics.BLOG_PUBLISHED,
                String.valueOf(event.getAuthorId()),
                event);
        log.info("blog published event saved to outbox, blogId={}, authorId={}",
                event.getBlogId(), event.getAuthorId());
    }

    public void publishAfterCommit(BlogPublishedEvent event) {
        enqueue(event);
    }
}
