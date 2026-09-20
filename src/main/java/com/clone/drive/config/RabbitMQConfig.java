package com.clone.drive.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "drive.events";
    public static final String FILE_UPLOADED_QUEUE = "drive.file.uploaded";
    public static final String FILE_UPLOADED_ROUTING_KEY = "file.uploaded";

    public static final String QUEUE_NOTIFICATIONS = "notifications-queue";
    public static final String ROUTING_KEY_NOTIFICATION = "notification.event";

    @Bean
    public TopicExchange driveExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue fileUploadedQueue() {
        return new Queue(FILE_UPLOADED_QUEUE, true);
    }

    @Bean
    public Queue notificationsQueue() {
        return new Queue(QUEUE_NOTIFICATIONS, true);
    }

    @Bean
    public Binding fileUploadedBinding(Queue fileUploadedQueue, TopicExchange driveExchange) {
        return BindingBuilder.bind(fileUploadedQueue).to(driveExchange).with(FILE_UPLOADED_ROUTING_KEY);
    }

    @Bean
    public Binding notificationsBinding(Queue notificationsQueue, TopicExchange driveExchange) {
        return BindingBuilder.bind(notificationsQueue).to(driveExchange).with(ROUTING_KEY_NOTIFICATION);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
