package com.hmdp.messaging;

import com.hmdp.event.BlogPublishedEvent;
import com.hmdp.service.MessageOutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BlogEventProducerTest {

    private BlogEventProducer producer;

    @Mock
    private MessageOutboxService messageOutboxService;

    @BeforeEach
    void setUp() {
        producer = new BlogEventProducer(messageOutboxService);
    }

    @Test
    void enqueue_shouldSaveBlogPublishedEventToOutbox() {
        BlogPublishedEvent event = new BlogPublishedEvent(1L, 2L, 3L);

        producer.enqueue(event);

        verify(messageOutboxService).enqueue(KafkaTopics.BLOG_PUBLISHED, "2", event);
    }

    @Test
    void publishAfterCommit_shouldDelegateToOutboxForBackwardCompatibility() {
        BlogPublishedEvent event = new BlogPublishedEvent(1L, 2L, 3L);

        producer.publishAfterCommit(event);

        verify(messageOutboxService).enqueue(KafkaTopics.BLOG_PUBLISHED, "2", event);
    }
}
