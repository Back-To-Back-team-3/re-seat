package com.backtoback.reseat.global.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

	public static final String QUEUE_ENTRY_REQUESTED_TOPIC = "queue.entry.requested.v1";
	public static final String QUEUE_ENTRY_CONSUMER_GROUP = "queue-entry-registration-v1";

	@Bean
	public NewTopic queueEntryRequestedTopic() {

		return TopicBuilder
			.name(QUEUE_ENTRY_REQUESTED_TOPIC)
			.partitions(3)
			.replicas(1)
			.build();
	}
}
