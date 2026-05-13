package com.hmdp.config;

import com.hmdp.messaging.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

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
    public NewTopic blogLikedTopic() {
        return  TopicBuilder.name(KafkaTopics.BLOG_LIKED)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
