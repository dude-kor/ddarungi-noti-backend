package kr.ddarungi.noti.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "push.exchange";
    public static final String QUEUE = "push.queue";
    public static final String ROUTING_KEY = "push.send";

    @Bean
    DirectExchange pushExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    /** Messages older than 60s are worthless for a time-sensitive alert, so let the broker drop them. */
    @Bean
    Queue pushQueue() {
        return QueueBuilder.durable(QUEUE).ttl(60_000).build();
    }

    @Bean
    Binding pushBinding(Queue pushQueue, DirectExchange pushExchange) {
        return BindingBuilder.bind(pushQueue).to(pushExchange).with(ROUTING_KEY);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper mapper = new DefaultJackson2JavaTypeMapper();
        mapper.setTrustedPackages("kr.ddarungi.noti.notification");
        converter.setJavaTypeMapper(mapper);
        return converter;
    }
}
