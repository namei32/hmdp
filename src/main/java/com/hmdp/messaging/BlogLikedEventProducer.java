package com.hmdp.messaging;

import com.hmdp.event.BlogLikedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CompletableFuture;

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
        CompletableFuture<SendResult<String, BlogLikedEvent>> future = kafkaTemplate.send(
                KafkaTopics.BLOG_LIKED,
                String.valueOf(event.getBlogId()),
                event);
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("blog liked event sent, blogId={}, userId={}, action={}",
                        event.getBlogId(), event.getUserId(), event.getAction());
                return;
            }
            log.warn("blog liked event send failed, skip best-effort event, blogId={}, userId={}, action={}",
                    event.getBlogId(), event.getUserId(), event.getAction(), ex);
        });
    }
}
