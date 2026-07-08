package com.marketmono.service.kafka;

import com.google.protobuf.InvalidProtocolBufferException;
import com.marketmono.entity.FlowAlert;
import com.marketmono.proto.client.FlowAlertProto;
import com.marketmono.repository.FlowAlertRepository;
import com.marketmono.service.FlowAlertBroadcaster;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Kafka consumer for the flow-alerts topic.
 * Lower volume (~100K/day, ~1-2 msg/sec) compared to option trades.
 * Persists to the existing flow_alerts table and broadcasts via WebSocket.
 */
@Service
@ConditionalOnProperty(name = "kafka.consumers.flow-alerts.enabled", havingValue = "true")
@Lazy(false)
@RequiredArgsConstructor
@Slf4j
public class KafkaFlowAlertConsumer {

    private final FlowAlertProtoMapper protoMapper;
    private final FlowAlertRepository flowAlertRepository;
    private final FlowAlertBroadcaster broadcaster;
    private final MeterRegistry meterRegistry;

    private Counter receivedCounter;
    private Counter persistedCounter;
    private Counter duplicateCounter;
    private Counter errorCounter;

    @PostConstruct
    public void initMetrics() {
        receivedCounter = meterRegistry.counter("kafka.flow_alerts.received");
        persistedCounter = meterRegistry.counter("kafka.flow_alerts.persisted");
        duplicateCounter = meterRegistry.counter("kafka.flow_alerts.duplicates");
        errorCounter = meterRegistry.counter("kafka.flow_alerts.errors");
        log.info("[KAFKA] FlowAlertConsumer initialized — listening on topic: {}", "flow-alerts");
    }

    @KafkaListener(
        topics = "${kafka.topics.flow-alerts:flow-alerts}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment ack) {
        receivedCounter.increment(records.size());
        log.debug("[KAFKA] Received batch of {} flow alert records from topic", records.size());

        int persisted = 0;
        int duplicates = 0;
        int errors = 0;

        for (ConsumerRecord<String, byte[]> consumerRecord : records) {
            try {
                FlowAlertProto.FlowAlert proto =
                    FlowAlertProto.FlowAlert.parseFrom(consumerRecord.value());

                FlowAlert entity = protoMapper.toEntity(proto);

                // Skip duplicates using the full Timescale-compatible composite key.
                if (flowAlertRepository.existsById(entity.getId())) {
                    duplicateCounter.increment();
                    duplicates++;
                    log.debug("[KAFKA] Skipping duplicate flow alert: id={}, ticker={}",
                        entity.getAlertId(), proto.getTicker());
                    continue;
                }

                flowAlertRepository.save(entity);
                persistedCounter.increment();
                persisted++;

                log.debug("[KAFKA] Flow alert saved: ticker={}, option={}, alertId={}, rule={}",
                    entity.getTicker(), entity.getOptionSymbol(), entity.getAlertId(), entity.getAlertRule());

                // Broadcast to frontend
                broadcaster.broadcastFlowAlert(entity);
                log.debug("[KAFKA] Broadcast flow alert to WebSocket: ticker={}, alertId={}",
                    entity.getTicker(), entity.getAlertId());

            } catch (InvalidProtocolBufferException e) {
                errorCounter.increment();
                errors++;
                log.error("[KAFKA] Failed to deserialize flow alert — topic={}, partition={}, offset={}: {}",
                    consumerRecord.topic(), consumerRecord.partition(), consumerRecord.offset(), e.getMessage());
            } catch (Exception e) {
                errorCounter.increment();
                errors++;
                log.error("[KAFKA] Failed to process flow alert — topic={}, partition={}, offset={}: {}",
                    consumerRecord.topic(), consumerRecord.partition(), consumerRecord.offset(), e.getMessage(), e);
            }
        }

        ack.acknowledge();
        log.info("[KAFKA] Flow alert batch complete: persisted={}, duplicates={}, errors={}, total={}",
            persisted, duplicates, errors, records.size());
    }
}
