package com.hmdp.messaging;

import com.hmdp.event.BlogLikedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.concurrent.ListenableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class BlogLikedEventProducer {

    private final KafkaTemplate<String, BlogLikedEvent> kafkaTemplate;

    public void publishAfterCommit(BlogLikedEvent event) {
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

    private void doPublish(BlogLikedEvent event) {
        ListenableFuture<SendResult<String, BlogLikedEvent>> future = kafkaTemplate.send(
                KafkaTopics.BLOG_LIKED,
                String.valueOf(event.getBlogId()),
                event);
        future.addCallback(
                result -> log.info("published blog liked event, blogId={},authorId={}, userId={},score={}, action={}",
                         event.getBlogId(), event.getAuthorId(), event.getUserId(), event.getScore(), event.getAction()),
                ex -> log.error("failed to publish blog liked event, blogId={},authorId={}, userId={},score={}, action={}",
                         event.getBlogId(), event.getAuthorId(), event.getUserId(), event.getScore(), event.getAction(), ex));
    }
}
