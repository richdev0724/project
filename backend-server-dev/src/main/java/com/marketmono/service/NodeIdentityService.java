package com.marketmono.service;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class NodeIdentityService {

    private final Environment environment;
    private final String nodeId;
    private final String lockOwnerId;
    private final String instanceUuid = UUID.randomUUID().toString();
    private final Instant startedAt = Instant.now();

    public NodeIdentityService(Environment environment) {
        this.environment = environment;
        this.lockOwnerId = resolveHostname(environment);
        this.nodeId = resolveNodeId(environment, lockOwnerId);
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getLockOwnerId() {
        return lockOwnerId;
    }

    public String getInstanceUuid() {
        return instanceUuid;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public long getUptimeSeconds() {
        return Duration.between(startedAt, Instant.now()).toSeconds();
    }

    public List<String> getActiveProfiles() {
        return Arrays.stream(environment.getActiveProfiles())
            .sorted()
            .toList();
    }

    private static String resolveNodeId(Environment environment, String fallbackHost) {
        String configured = environment.getProperty("node.id");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return fallbackHost;
    }

    private static String resolveHostname(Environment environment) {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            if (hostname != null && !hostname.isBlank()) {
                return hostname;
            }
        } catch (Exception ignored) {
            // Fall through to env-based resolution.
        }

        String hostname = environment.getProperty("HOSTNAME");
        if (hostname != null && !hostname.isBlank()) {
            return hostname;
        }

        String computerName = environment.getProperty("COMPUTERNAME");
        if (computerName != null && !computerName.isBlank()) {
            return computerName;
        }

        return "unknown";
    }
}