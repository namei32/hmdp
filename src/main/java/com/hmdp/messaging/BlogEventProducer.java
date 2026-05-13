package com.hmdp.messaging;

import com.hmdp.event.BlogPublishedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
        ListenableFuture<SendResult<String, BlogPublishedEvent>> future = kafkaTemplate.send(
                KafkaTopics.BLOG_PUBLISHED,
                String.valueOf(event.getAuthorId()),
                event);
        future.addCallback(
                result -> log.info("published blog event, blogId={}, authorId={}", event.getBlogId(), event.getAuthorId()),
                ex -> log.error("failed to publish blog event, blogId={}, authorId={}",
                        event.getBlogId(), event.getAuthorId(), ex));
    }
}
