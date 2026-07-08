package com.marketmono.service.kafka;

import com.google.protobuf.InvalidProtocolBufferException;
import com.marketmono.config.KafkaConfig;
import com.marketmono.entity.OptionTrade;
import com.marketmono.proto.client.TransactionsProto;
import com.marketmono.service.OptionTradeAggregationCoordinator;
import com.marketmono.service.OptionTradeBroadcaster;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Kafka batch consumer for the all-option-trades topic.
 * Deserializes protobuf messages, maps to entities, persists in batch,
 * and broadcasts to WebSocket subscribers.
 *
 * Expected throughput: 6-10M messages/day (~250-400 msg/sec avg, 1000+ peak).
 */
@Service
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
@Lazy(false)
@RequiredArgsConstructor
@Slf4j
public class KafkaOptionTradeConsumer {

    private final OptionTradeProtoMapper protoMapper;
    private final OptionTradeBatchPersistence batchPersistence;
    private final OptionTradeBroadcaster broadcaster;
    private final OptionTradeAggregationCoordinator aggregationCoordinator;
    private final KafkaDeadLetterPublisher deadLetterPublisher;
    private final KafkaConfig kafkaConfig;
    private final MeterRegistry meterRegistry;

    private Counter receivedCounter;
    private Counter deserErrorCounter;
    private Counter mapErrorCounter;
    private Counter retryCounter;
    private Counter deadLetterCounter;

    @PostConstruct
    public void initMetrics() {
        receivedCounter = meterRegistry.counter("kafka.option_trades.received");
        deserErrorCounter = meterRegistry.counter("kafka.option_trades.deser_errors");
        mapErrorCounter = meterRegistry.counter("kafka.option_trades.map_errors");
        retryCounter = meterRegistry.counter("kafka.option_trades.persistence_retries");
        deadLetterCounter = meterRegistry.counter("kafka.option_trades.dead_lettered");
        log.info("[KAFKA] OptionTradeConsumer initialized — listening on topic: {}", "all-option-trades");
    }

    @KafkaListener(
        topics = "${kafka.topics.option-trades:all-option-trades}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment ack) {
        long batchStart = System.nanoTime();
        receivedCounter.increment(records.size());
        log.debug("[KAFKA] Received batch of {} option trade records from topic", records.size());

        List<OptionTrade> entities = new ArrayList<>(records.size());
        int deadLettered = 0;

        for (ConsumerRecord<String, byte[]> consumerRecord : records) {
            try {
                TransactionsProto.OptionTrade proto =
                    TransactionsProto.OptionTrade.parseFrom(consumerRecord.value());

                OptionTrade entity = protoMapper.toEntity(proto);
                if (entity != null) {
                    entities.add(entity);
                }

            } catch (InvalidProtocolBufferException e) {
                deserErrorCounter.increment();
                log.error("[KAFKA] Failed to deserialize option trade — topic={}, partition={}, offset={}: {}",
                    consumerRecord.topic(), consumerRecord.partition(), consumerRecord.offset(), e.getMessage());
                if (!deadLetterPublisher.publishOptionTradeRecord(consumerRecord, "deserialization-failed", e)) {
                    log.error("[KAFKA] Dead-letter publish failed for malformed record — batch will NOT ack so Kafka can redeliver");
                    return;
                }
                deadLettered++;
            } catch (Exception e) {
                mapErrorCounter.increment();
                log.error("[KAFKA] Failed to map option trade — topic={}, partition={}, offset={}: {}",
                    consumerRecord.topic(), consumerRecord.partition(), consumerRecord.offset(), e.getMessage(), e);
                if (!deadLetterPublisher.publishOptionTradeRecord(consumerRecord, "mapping-failed", e)) {
                    log.error("[KAFKA] Dead-letter publish failed for unmappable record — batch will NOT ack so Kafka can redeliver");
                    return;
                }
                deadLettered++;
            }
        }

        // Persist batch
        int deserErrors = records.size() - entities.size();
        if (deserErrors > 0) {
            log.warn("[KAFKA] Skipped {} records due to deserialization/mapping errors in batch of {}",
                deserErrors, records.size());
        }

        if (!entities.isEmpty()) {
            if (!persistWithRetry(records, entities)) {
                return;
            }

            // Broadcast latest trades to WebSocket subscribers (non-blocking)
            broadcaster.broadcastTrades(entities);

            // Derive aggregated feed rows asynchronously from the persisted raw trades.
            aggregationCoordinator.aggregateBatch(entities);
        }

        // Commit offsets after successful processing
        if (deadLettered > 0) {
            deadLetterCounter.increment(deadLettered);
            log.warn("[KAFKA] Dead-lettered {} option trade records from this batch", deadLettered);
        }
        ack.acknowledge();
        long batchElapsedMs = (System.nanoTime() - batchStart) / 1_000_000;
        log.debug("[KAFKA] Batch fully processed: {} records in {}ms (deser+map+persist+broadcast)",
            records.size(), batchElapsedMs);
    }

    private boolean persistWithRetry(List<ConsumerRecord<String, byte[]>> records, List<OptionTrade> entities) {
        int maxAttempts = kafkaConfig.getDeadLetter().getRetryAttempts();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                batchPersistence.persistBatch(entities);
                log.debug("[KAFKA] Batch persisted: {} trades saved (from {} records received) on attempt {}/{}",
                    entities.size(), records.size(), attempt, maxAttempts);
                return true;
            } catch (Exception e) {
                if (attempt < maxAttempts) {
                    retryCounter.increment();
                    log.warn("[KAFKA] Batch persistence attempt {}/{} failed for {} trades. Retrying in {}ms. Error: {}",
                        attempt, maxAttempts, entities.size(), kafkaConfig.getDeadLetter().getRetryBackoffMs(), e.getMessage());
                    sleepBeforeRetry();
                    continue;
                }

                log.error("[KAFKA] Batch persistence failed after {} attempts for {} trades. Publishing entire batch to DLT.",
                    maxAttempts, entities.size(), e);

                if (!deadLetterPublisher.publishOptionTradeBatch(records, "batch-persistence-failed", e)) {
                    log.error("[KAFKA] Failed to publish exhausted batch to DLT — Kafka offsets will NOT be acknowledged");
                    return false;
                }

                deadLetterCounter.increment(records.size());
                return false;
            }
        }

        return false;
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(kafkaConfig.getDeadLetter().getRetryBackoffMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[KAFKA] Retry backoff interrupted; continuing immediately");
        }
    }
}
