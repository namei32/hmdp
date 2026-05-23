package com.hmdp.messaging;

import com.hmdp.event.BlogPublishedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class BlogEventProducer {

    private final KafkaTemplate<String, BlogPublishedEvent> kafkaTemplate;

    public void publishAfterCommit(BlogPublishedEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doPublish(event);
                }
            });
            return;
        }
        doPublish(event);
    }

    private void doPublish(BlogPublishedEvent event) {
        CompletableFuture<SendResult<String, BlogPublishedEvent>> future = kafkaTemplate.send(
                KafkaTopics.BLOG_PUBLISHED,
                String.valueOf(event.getAuthorId()),
                event);
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("published blog event, blogId={}, authorId={}", event.getBlogId(), event.getAuthorId());
                return;
            }
            log.error("failed to publish blog event, blogId={}, authorId={}",
                    event.getBlogId(), event.getAuthorId(), ex);
        });
    }
}
