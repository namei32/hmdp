package com.hmdp.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.MessageOutbox;
import com.hmdp.mapper.MessageOutboxMapper;
import com.hmdp.messaging.MessageOutboxStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageOutboxService extends ServiceImpl<MessageOutboxMapper, MessageOutbox> {
    private static final int MAX_RETRY_DELAY_SECONDS = 300;
    private static final int SENDING_TIMEOUT_SECONDS = 120;

    public void enqueue(String topic, String messageKey, Object payload) {
        LocalDateTime now = LocalDateTime.now();
        MessageOutbox message = new MessageOutbox()
                .setTopic(topic)
                .setMessageKey(messageKey)
                .setPayload(JSONUtil.toJsonStr(payload))
                .setStatus(MessageOutboxStatus.INIT)
                .setRetryCount(0)
                .setNextRetryTime(now)
                .setCreateTime(now)
                .setUpdateTime(now);
        save(message);
    }

    public List<MessageOutbox> claimDueMessages(int batchSize) {
        LocalDateTime now = LocalDateTime.now();
        List<MessageOutbox> dueMessages = lambdaQuery()
                .and(wrapper -> wrapper
                        .in(MessageOutbox::getStatus, MessageOutboxStatus.INIT, MessageOutboxStatus.FAILED)
                        .le(MessageOutbox::getNextRetryTime, now)
                        .or()
                        .eq(MessageOutbox::getStatus, MessageOutboxStatus.SENDING)
                        .le(MessageOutbox::getNextRetryTime, now))
                .orderByAsc(MessageOutbox::getNextRetryTime)
                .last("LIMIT " + batchSize)
                .list();

        return dueMessages.stream()
                .filter(this::claimMessage)
                .toList();
    }

    public void markSent(Long id) {
        lambdaUpdate()
                .eq(MessageOutbox::getId, id)
                .set(MessageOutbox::getStatus, MessageOutboxStatus.SENT)
                .set(MessageOutbox::getUpdateTime, LocalDateTime.now())
                .update();
    }

    public void markFailed(MessageOutbox message) {
        int retryCount = message.getRetryCount() == null ? 1 : message.getRetryCount() + 1;
        LocalDateTime nextRetryTime = LocalDateTime.now().plusSeconds(calculateRetryDelaySeconds(retryCount));
        lambdaUpdate()
                .eq(MessageOutbox::getId, message.getId())
                .set(MessageOutbox::getStatus, MessageOutboxStatus.FAILED)
                .set(MessageOutbox::getRetryCount, retryCount)
                .set(MessageOutbox::getNextRetryTime, nextRetryTime)
                .set(MessageOutbox::getUpdateTime, LocalDateTime.now())
                .update();
    }

    private boolean claimMessage(MessageOutbox message) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sendingDeadline = now.plusSeconds(SENDING_TIMEOUT_SECONDS);
        return lambdaUpdate()
                .eq(MessageOutbox::getId, message.getId())
                .eq(MessageOutbox::getStatus, message.getStatus())
                .set(MessageOutbox::getStatus, MessageOutboxStatus.SENDING)
                .set(MessageOutbox::getNextRetryTime, sendingDeadline)
                .set(MessageOutbox::getUpdateTime, now)
                .update();
    }

    private int calculateRetryDelaySeconds(int retryCount) {
        int delay = 1 << Math.min(retryCount, 8);
        return Math.min(delay, MAX_RETRY_DELAY_SECONDS);
    }
}
