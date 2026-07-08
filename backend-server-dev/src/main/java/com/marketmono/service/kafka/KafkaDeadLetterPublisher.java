package com.marketmono.service.kafka;

import com.marketmono.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Publishes failed Kafka records to a dead-letter topic after retries are exhausted.
 */
@Service
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
@Lazy(false)
@RequiredArgsConstructor
@Slf4j
public class KafkaDeadLetterPublisher {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final KafkaConfig kafkaConfig;

    public boolean publishOptionTradeRecord(ConsumerRecord<String, byte[]> consumerRecord, String reason, Exception exception) {
        String topic = kafkaConfig.getDeadLetter().getOptionTradesTopic();

        try {
            ProducerRecord<String, byte[]> producerRecord = new ProducerRecord<>(topic, null, consumerRecord.key(), consumerRecord.value(), new RecordHeaders());
            producerRecord.headers().add("x-original-topic", consumerRecord.topic().getBytes(StandardCharsets.UTF_8));
            producerRecord.headers().add("x-original-partition", String.valueOf(consumerRecord.partition()).getBytes(StandardCharsets.UTF_8));
            producerRecord.headers().add("x-original-offset", String.valueOf(consumerRecord.offset()).getBytes(StandardCharsets.UTF_8));
            producerRecord.headers().add("x-failure-reason", reason.getBytes(StandardCharsets.UTF_8));
            producerRecord.headers().add("x-exception-type", exception.getClass().getName().getBytes(StandardCharsets.UTF_8));

            kafkaTemplate.send(producerRecord).get(10, TimeUnit.SECONDS);
            log.error("[KAFKA] Published failed option trade to DLT topic={} reason={} originalTopic={} partition={} offset={}",
                topic, reason, consumerRecord.topic(), consumerRecord.partition(), consumerRecord.offset());
            return true;
        } catch (Exception publishException) {
            log.error("[KAFKA] FAILED to publish record to DLT topic={} originalTopic={} partition={} offset={}: {}",
                topic, consumerRecord.topic(), consumerRecord.partition(), consumerRecord.offset(), publishException.getMessage(), publishException);
            return false;
        }
    }

    public boolean publishOptionTradeBatch(List<ConsumerRecord<String, byte[]>> records, String reason, Exception exception) {
        for (ConsumerRecord<String, byte[]> consumerRecord : records) {
            if (!publishOptionTradeRecord(consumerRecord, reason, exception)) {
                return false;
            }
        }

        return true;
    }
}
