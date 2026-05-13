package com.hmdp.messaging;

import com.hmdp.event.BlogPublishedEvent;
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
import org.springframework.util.concurrent.SettableListenableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class BlogEventProducerTest {

    private BlogEventProducer producer;

    @Mock
    private KafkaTemplate<String, BlogPublishedEvent> kafkaTemplate;

    private SettableListenableFuture<SendResult<String, BlogPublishedEvent>> sendFuture;

    @BeforeEach
    void setUp() {
        producer = new BlogEventProducer(kafkaTemplate);
        sendFuture = new SettableListenableFuture<>();
        doReturn(sendFuture).when(kafkaTemplate).send(anyString(), anyString(), any(BlogPublishedEvent.class));
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishAfterCommit_shouldSendImmediatelyWithoutTransaction() {
        producer.publishAfterCommit(new BlogPublishedEvent(1L, 2L, 3L));

        verify(kafkaTemplate).send(KafkaTopics.BLOG_PUBLISHED, "2", new BlogPublishedEvent(1L, 2L, 3L));
    }

    @Test
    void publishAfterCommit_shouldDeferSendUntilCommit() {
        TransactionSynchronizationManager.initSynchronization();

        BlogPublishedEvent event = new BlogPublishedEvent(1L, 2L, 3L);
        producer.publishAfterCommit(event);

        verifyNoInteractions(kafkaTemplate);
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        verify(kafkaTemplate).send(KafkaTopics.BLOG_PUBLISHED, "2", event);
    }
}
