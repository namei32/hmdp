package com.hmdp.messaging;

import com.hmdp.event.BlogLikedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class BlogLikedEventProducerTest {

    private BlogLikedEventProducer producer;

    @Mock
    private KafkaTemplate<String, BlogLikedEvent> kafkaTemplate;

    private CompletableFuture<SendResult<String, BlogLikedEvent>> sendFuture;

    @BeforeEach
    void setUp() {
        producer = new BlogLikedEventProducer(kafkaTemplate);
        sendFuture = new CompletableFuture<>();
        doReturn(sendFuture).when(kafkaTemplate).send(anyString(), anyString(), any(BlogLikedEvent.class));
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishAfterCommit_shouldSendImmediatelyWithoutTransaction() {
        BlogLikedEvent event = new BlogLikedEvent(1L, 2L, 3L, 4D, true);

        producer.publishAfterCommit(event);

        verify(kafkaTemplate).send(KafkaTopics.BLOG_LIKED, "1", event);
    }

    @Test
    void publishAfterCommit_shouldDeferSendUntilCommit() {
        TransactionSynchronizationManager.initSynchronization();
        BlogLikedEvent event = new BlogLikedEvent(1L, 2L, 3L, 4D, true);

        producer.publishAfterCommit(event);

        verifyNoInteractions(kafkaTemplate);
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        verify(kafkaTemplate).send(KafkaTopics.BLOG_LIKED, "1", event);
    }
}
