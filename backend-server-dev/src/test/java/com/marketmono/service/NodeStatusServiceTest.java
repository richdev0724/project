package com.marketmono.service;

import com.marketmono.dto.NodeStatusResponse;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NodeStatusServiceTest {

    @Mock
    private NodeIdentityService nodeIdentityService;

    @Mock
    private Environment environment;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DataSource dataSource;

    @Mock
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Mock
    private MessageListenerContainer messageListenerContainer;

    @Mock
    private SimpUserRegistry simpUserRegistry;

    @Mock
    private SimpUser simpUser;

    @Mock
    private SimpSession simpSessionOne;

    @Mock
    private SimpSession simpSessionTwo;

    @Mock
    private RedisConnectionFactory redisConnectionFactory;

    @Mock
    private RedisConnection redisConnection;

    @Mock
    private HikariDataSource hikariDataSource;

    @Mock
    private HikariPoolMXBean hikariPoolMXBean;

    private NodeStatusService nodeStatusService;

    @BeforeEach
    void setUp() throws Exception {
        nodeStatusService = new NodeStatusService(
            nodeIdentityService,
            environment,
            jdbcTemplate,
            dataSource,
            Optional.of(kafkaListenerEndpointRegistry),
            Optional.of(simpUserRegistry),
            Optional.of(redisConnectionFactory)
        );

        when(nodeIdentityService.getNodeId()).thenReturn("backend-a-prod");
        when(nodeIdentityService.getInstanceUuid()).thenReturn("instance-uuid");
        when(nodeIdentityService.getStartedAt()).thenReturn(Instant.parse("2026-04-12T10:30:00Z"));
        when(nodeIdentityService.getUptimeSeconds()).thenReturn(3600L);
        when(nodeIdentityService.getActiveProfiles()).thenReturn(List.of("active-active", "prod"));
        when(nodeIdentityService.getLockOwnerId()).thenReturn("backend-host");

        when(environment.getProperty("spring.kafka.enabled", Boolean.class, false)).thenReturn(true);
        when(environment.getProperty("websocket.relay.enabled", Boolean.class, false)).thenReturn(true);
        when(environment.getProperty("active-active.redis.enabled", Boolean.class, false)).thenReturn(true);

        when(kafkaListenerEndpointRegistry.getListenerContainers()).thenReturn(List.of(messageListenerContainer));
        when(messageListenerContainer.isRunning()).thenReturn(true);

        when(simpUserRegistry.getUsers()).thenReturn(Set.of(simpUser));
        when(simpUser.getSessions()).thenReturn(Set.of(simpSessionOne, simpSessionTwo));

        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");

        when(dataSource.unwrap(HikariDataSource.class)).thenReturn(hikariDataSource);
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        when(hikariDataSource.getMaximumPoolSize()).thenReturn(35);
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(12);
        when(hikariPoolMXBean.getThreadsAwaitingConnection()).thenReturn(1);

        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("backend-host")))
            .thenReturn(List.of("cacheRefresh_marketTide"));
    }

    @Test
    void shouldAssembleNodeStatusFromAvailableSubsystems() {
        NodeStatusResponse response = nodeStatusService.getStatus();

        assertThat(response.nodeId()).isEqualTo("backend-a-prod");
        assertThat(response.instanceUuid()).isEqualTo("instance-uuid");
        assertThat(response.startedAt()).isEqualTo(Instant.parse("2026-04-12T10:30:00Z"));
        assertThat(response.uptimeSeconds()).isEqualTo(3600L);
        assertThat(response.profiles()).containsExactly("active-active", "prod");

        assertThat(response.kafka().enabled()).isTrue();
        assertThat(response.kafka().listenersRunning()).isEqualTo(1);
        assertThat(response.kafka().listenersTotal()).isEqualTo(1);

        assertThat(response.websocket().relayEnabled()).isTrue();
        assertThat(response.websocket().relayType()).isEqualTo("redis");
        assertThat(response.websocket().activeSessions()).isEqualTo(2);

        assertThat(response.redis().connected()).isTrue();
        assertThat(response.redis().cacheEnabled()).isTrue();

        assertThat(response.database().activeConnections()).isEqualTo(12);
        assertThat(response.database().maxPoolSize()).isEqualTo(35);
        assertThat(response.database().pendingAcquire()).isEqualTo(1);

        assertThat(response.shedlock().activeLocksHeld()).containsExactly("cacheRefresh_marketTide");
    }

    @Test
    void shouldReturnSafeDefaultsWhenOptionalSubsystemsAreDisabled() throws Exception {
        nodeStatusService = new NodeStatusService(
            nodeIdentityService,
            environment,
            jdbcTemplate,
            dataSource,
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );

        when(environment.getProperty("spring.kafka.enabled", Boolean.class, false)).thenReturn(false);
        when(environment.getProperty("websocket.relay.enabled", Boolean.class, false)).thenReturn(false);
        when(environment.getProperty("active-active.redis.enabled", Boolean.class, false)).thenReturn(false);
        when(dataSource.unwrap(HikariDataSource.class)).thenThrow(new IllegalStateException("no hikari"));
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("backend-host")))
            .thenThrow(new IllegalStateException("no shedlock"));

        NodeStatusResponse response = nodeStatusService.getStatus();

        assertThat(response.kafka().enabled()).isFalse();
        assertThat(response.kafka().listenersRunning()).isZero();
        assertThat(response.websocket().relayEnabled()).isFalse();
        assertThat(response.websocket().activeSessions()).isZero();
        assertThat(response.redis().connected()).isFalse();
        assertThat(response.redis().cacheEnabled()).isFalse();
        assertThat(response.database().maxPoolSize()).isZero();
        assertThat(response.shedlock().activeLocksHeld()).isEmpty();
    }
}