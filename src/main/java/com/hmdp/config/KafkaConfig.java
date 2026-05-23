package com.hmdp.config;

import com.hmdp.messaging.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Bean
    public NewTopic blogPublishedTopic() {
        return TopicBuilder.name(KafkaTopics.BLOG_PUBLISHED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic blogPublishedDltTopic() {
        return TopicBuilder.name(KafkaTopics.BLOG_PUBLISHED_DLT)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic blogLikedTopic() {
        return TopicBuilder.name(KafkaTopics.BLOG_LIKED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic blogLikedDltTopic() {
        return TopicBuilder.name(KafkaTopics.BLOG_LIKED_DLT)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic seckillOrderTopic() {
        return TopicBuilder.name(KafkaTopics.SECKILL_ORDER)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic seckillOrderDltTopic() {
        return TopicBuilder.name(KafkaTopics.SECKILL_ORDER_DLT)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".dlt", record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    }
}
