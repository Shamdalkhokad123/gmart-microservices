package com.gmart.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "gmart.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTopicConfig {

    /** Created automatically on startup if it doesn't exist yet. */
    @Bean
    public NewTopic ordersTopic(@Value("${gmart.kafka.topic.orders}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderStatusTopic(@Value("${gmart.kafka.topic.order-status}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
