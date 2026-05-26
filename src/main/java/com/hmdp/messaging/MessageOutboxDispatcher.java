package com.hmdp.messaging;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.MessageOutbox;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.event.BlogPublishedEvent;
import com.hmdp.service.MessageOutboxService;
import com.hmdp.service.SeckillOrderConsistencyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class MessageOutboxDispatcher {
    private static final int DEFAULT_BATCH_SIZE = 100;

    private final MessageOutboxService messageOutboxService;
    private final SeckillOrderConsistencyService seckillOrderConsistencyService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public MessageOutboxDispatcher(
            MessageOutboxService messageOutboxService,
            SeckillOrderConsistencyService seckillOrderConsistencyService,
            KafkaTemplate<String, Object> kafkaTemplate) {
        this.messageOutboxService = messageOutboxService;
        this.seckillOrderConsistencyService = seckillOrderConsistencyService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${hmdp.outbox.publish-fixed-delay:5000}")
    public void publishDueMessages() {
        List<MessageOutbox> messages = messageOutboxService.claimDueMessages(DEFAULT_BATCH_SIZE);
        for (MessageOutbox message : messages) {
            publish(message);
        }
    }

    private void publish(MessageOutbox message) {
        Object payload;
        try {
            payload = deserializePayload(message.getTopic(), message.getPayload());
        } catch (RuntimeException e) {
            log.error("outbox message deserialize failed, id={}, topic={}", message.getId(), message.getTopic(), e);
            messageOutboxService.markFailed(message);
            return;
        }

        CompletableFuture<?> future = kafkaTemplate.send(message.getTopic(), message.getMessageKey(), payload);
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                messageOutboxService.markSent(message.getId());
                markBusinessMessageSent(payload);
                log.info("outbox message sent, id={}, topic={}, key={}",
                        message.getId(), message.getTopic(), message.getMessageKey());
                return;
            }
            log.error("outbox message send failed, id={}, topic={}, key={}",
                    message.getId(), message.getTopic(), message.getMessageKey(), ex);
            messageOutboxService.markFailed(message);
        });
    }

    private Object deserializePayload(String topic, String payload) {
        return switch (topic) {
            case KafkaTopics.BLOG_PUBLISHED -> JSONUtil.toBean(payload, BlogPublishedEvent.class);
            case KafkaTopics.SECKILL_ORDER -> JSONUtil.toBean(payload, VoucherOrder.class);
            default -> throw new IllegalArgumentException("unsupported outbox topic: " + topic);
        };
    }

    private void markBusinessMessageSent(Object payload) {
        try {
            if (payload instanceof VoucherOrder voucherOrder) {
                seckillOrderConsistencyService.markSent(voucherOrder.getId());
            }
        } catch (RuntimeException e) {
            log.error("mark business message sent failed, payload={}", payload, e);
        }
    }
}
