package com.skaanb.DejaView.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String TICKET_ANALYSIS_EXCHANGE = "dejaview.tickets.exchange";
    public static final String TICKET_ANALYSIS_QUEUE = "dejaview.tickets.analysis.queue";
    public static final String TICKET_ANALYSIS_ROUTING_KEY = "ticket.analysis";

    @Bean
    public DirectExchange ticketAnalysisExchange() {
        return new DirectExchange(TICKET_ANALYSIS_EXCHANGE, true, false);
    }

    @Bean
    public Queue ticketAnalysisQueue() {
        // durable=true: RabbitMQ yeniden başlasa bile kuyruktaki mesajlar kaybolmasın
        return new Queue(TICKET_ANALYSIS_QUEUE, true);
    }

    @Bean
    public Binding ticketAnalysisBinding(Queue ticketAnalysisQueue, DirectExchange ticketAnalysisExchange) {
        return BindingBuilder.bind(ticketAnalysisQueue)
                .to(ticketAnalysisExchange)
                .with(TICKET_ANALYSIS_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
