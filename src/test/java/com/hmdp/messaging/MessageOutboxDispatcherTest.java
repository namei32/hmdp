package com.hmdp.messaging;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.MessageOutbox;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.event.BlogPublishedEvent;
import com.hmdp.service.MessageOutboxService;
import com.hmdp.service.SeckillOrderConsistencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageOutboxDispatcherTest {

    private MessageOutboxDispatcher dispatcher;

    @Mock
    private MessageOutboxService messageOutboxService;
    @Mock
    private SeckillOrderConsistencyService seckillOrderConsistencyService;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void setUp() {
        dispatcher = new MessageOutboxDispatcher(
                messageOutboxService,
                seckillOrderConsistencyService,
                kafkaTemplate);
    }

    @Test
    void publishDueMessages_shouldSendSeckillOrderAndMarkSent() {
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(10L);
        voucherOrder.setUserId(20L);
        voucherOrder.setVoucherId(30L);

        MessageOutbox message = new MessageOutbox()
                .setId(1L)
                .setTopic(KafkaTopics.SECKILL_ORDER)
                .setMessageKey("10")
                .setPayload(JSONUtil.toJsonStr(voucherOrder));

        CompletableFuture<SendResult<String, Object>> sendFuture = new CompletableFuture<>();
        sendFuture.complete(null);
        when(messageOutboxService.claimDueMessages(100)).thenReturn(List.of(message));
        when(kafkaTemplate.send(eq(KafkaTopics.SECKILL_ORDER), eq("10"), any(Object.class)))
                .thenReturn(sendFuture);

        dispatcher.publishDueMessages();

        verify(messageOutboxService).markSent(1L);
        verify(seckillOrderConsistencyService).markSent(10L);
    }

    @Test
    void publishDueMessages_shouldMarkFailedWhenSendFails() {
        MessageOutbox message = new MessageOutbox()
                .setId(1L)
                .setTopic(KafkaTopics.BLOG_PUBLISHED)
                .setMessageKey("2")
                .setPayload("{\"blogId\":1,\"authorId\":2,\"publishedAt\":3}");

        CompletableFuture<SendResult<String, Object>> sendFuture = new CompletableFuture<>();
        sendFuture.completeExceptionally(new RuntimeException("kafka unavailable"));
        when(messageOutboxService.claimDueMessages(100)).thenReturn(List.of(message));
        when(kafkaTemplate.send(eq(KafkaTopics.BLOG_PUBLISHED), eq("2"), any(BlogPublishedEvent.class)))
                .thenReturn(sendFuture);

        dispatcher.publishDueMessages();

        verify(messageOutboxService).markFailed(message);
    }
}
