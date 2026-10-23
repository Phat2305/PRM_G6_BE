
package com.example.art_course.video.config;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class VideoRabbitConfig {

    public static final String VIDEO_EXCHANGE = "video.exchange";
    public static final String VIDEO_QUEUE = "video.processing.queue";
    public static final String VIDEO_ROUTING_KEY = "video.processing";

    @Bean
    public DirectExchange videoExchange() {
        return new DirectExchange(VIDEO_EXCHANGE, true, false);
    }

    @Bean
    public Queue videoQueue() {
        return QueueBuilder.durable(VIDEO_QUEUE).build();
    }

    @Bean
    public Binding videoBinding(
            Queue videoQueue,
            DirectExchange videoExchange) {
        return BindingBuilder.bind(videoQueue)
                .to(videoExchange)
                .with(VIDEO_ROUTING_KEY);
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }


    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }


    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory =
                new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        // shortcut: lỗi bị bỏ message, chưa có DLQ; thêm DLX khi cần xử lý lại job lỗi
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    @Bean
    public ApplicationRunner checkRabbitConnection(
            ConnectionFactory connectionFactory) {
        return args -> {
            try {
                connectionFactory.createConnection().close();
                System.out.println("========== RABBITMQ CONNECTED ==========");
            } catch (Exception e) {
                System.err.println("========== RABBITMQ CONNECTION FAILED ==========");
                e.printStackTrace();
            }
        };
    }

    @Bean
    public ApplicationRunner declareVideoQueue(RabbitAdmin rabbitAdmin) {
        return args -> {
            rabbitAdmin.initialize();
            System.out.println("Video RabbitMQ declarations initialized");
        };
    }
}
