package com.marketmono.service;

import com.marketmono.dto.NodeStatusResponse;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NodeStatusService {

    private final NodeIdentityService nodeIdentityService;
    private final Environment environment;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final Optional<KafkaListenerEndpointRegistry> kafkaListenerEndpointRegistry;
    private final Optional<SimpUserRegistry> simpUserRegistry;
    private final Optional<RedisConnectionFactory> redisConnectionFactory;

    public NodeStatusResponse getStatus() {
        return new NodeStatusResponse(
            nodeIdentityService.getNodeId(),
            nodeIdentityService.getInstanceUuid(),
            nodeIdentityService.getStartedAt(),
            nodeIdentityService.getUptimeSeconds(),
            nodeIdentityService.getActiveProfiles(),
            kafkaStatus(),
            webSocketStatus(),
            redisStatus(),
            databaseStatus(),
            shedLockStatus()
        );
    }

    private NodeStatusResponse.KafkaStatus kafkaStatus() {
        boolean kafkaEnabled = environment.getProperty("spring.kafka.enabled", Boolean.class, false);
        if (!kafkaEnabled) {
            return new NodeStatusResponse.KafkaStatus(false, 0, 0, List.of());
        }

        return kafkaListenerEndpointRegistry
            .map(registry -> {
                Collection<MessageListenerContainer> containers = registry.getListenerContainers();
                int listenersTotal = containers.size();
                int listenersRunning = (int) containers.stream()
                    .filter(MessageListenerContainer::isRunning)
                    .count();
                List<String> partitionsAssigned = containers.stream()
                    .flatMap(container -> extractAssignedPartitions(container).stream())
                    .distinct()
                    .sorted()
                    .toList();

                return new NodeStatusResponse.KafkaStatus(true, listenersRunning, listenersTotal, partitionsAssigned);
            })
            .orElseGet(() -> new NodeStatusResponse.KafkaStatus(true, 0, 0, List.of()));
    }

    private List<String> extractAssignedPartitions(MessageListenerContainer container) {
        try {
            Method method = container.getClass().getMethod("getAssignedPartitions");
            Object value = method.invoke(container);
            if (value instanceof Collection<?> partitions) {
                return partitions.stream()
                    .filter(TopicPartition.class::isInstance)
                    .map(TopicPartition.class::cast)
                    .map(partition -> partition.topic() + "-" + partition.partition())
                    .toList();
            }
        } catch (NoSuchMethodException ignored) {
            // Container implementation does not expose assignments directly.
        } catch (Exception exception) {
            log.debug("Unable to inspect Kafka partition assignments for {}: {}",
                container.getClass().getSimpleName(), exception.getMessage());
        }

        return List.of();
    }

    private NodeStatusResponse.WebSocketStatus webSocketStatus() {
        boolean relayEnabled = environment.getProperty("websocket.relay.enabled", Boolean.class, false);
        int activeSessions = simpUserRegistry
            .map(this::countActiveSessions)
            .orElse(0);

        return new NodeStatusResponse.WebSocketStatus(
            relayEnabled,
            relayEnabled ? "redis" : "local",
            activeSessions
        );
    }

    private int countActiveSessions(SimpUserRegistry registry) {
        return registry.getUsers().stream()
            .mapToInt(this::sessionCount)
            .sum();
    }

    private int sessionCount(SimpUser user) {
        return user.getSessions().size();
    }

    private NodeStatusResponse.RedisStatus redisStatus() {
        boolean cacheEnabled = environment.getProperty("active-active.redis.enabled", Boolean.class, false);
        boolean connected = redisConnectionFactory
            .map(this::isRedisConnected)
            .orElse(false);

        return new NodeStatusResponse.RedisStatus(connected, cacheEnabled);
    }

    private boolean isRedisConnected(RedisConnectionFactory connectionFactory) {
        RedisConnection connection = null;
        try {
            connection = connectionFactory.getConnection();
            String ping = connection.ping();
            return ping != null;
        } catch (Exception exception) {
            log.warn("Redis connectivity check failed for node status: {}", exception.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    private NodeStatusResponse.DatabaseStatus databaseStatus() {
        try {
            HikariDataSource hikariDataSource = dataSource.unwrap(HikariDataSource.class);
            HikariPoolMXBean poolMxBean = hikariDataSource.getHikariPoolMXBean();

            int activeConnections = poolMxBean != null ? poolMxBean.getActiveConnections() : 0;
            int pendingAcquire = poolMxBean != null ? poolMxBean.getThreadsAwaitingConnection() : 0;

            return new NodeStatusResponse.DatabaseStatus(
                activeConnections,
                hikariDataSource.getMaximumPoolSize(),
                pendingAcquire
            );
        } catch (Exception exception) {
            log.warn("Database pool inspection failed for node status: {}", exception.getMessage());
            return new NodeStatusResponse.DatabaseStatus(0, 0, 0);
        }
    }

    private NodeStatusResponse.ShedLockStatus shedLockStatus() {
        try {
            List<String> activeLocks = jdbcTemplate.queryForList(
                "select name from shedlock where lock_until > CURRENT_TIMESTAMP and locked_by = ? order by name",
                String.class,
                nodeIdentityService.getLockOwnerId()
            );
            return new NodeStatusResponse.ShedLockStatus(activeLocks);
        } catch (Exception exception) {
            log.warn("ShedLock inspection failed for node status: {}", exception.getMessage());
            return new NodeStatusResponse.ShedLockStatus(List.of());
        }
    }
}