package com.marketmono.config;

import com.marketmono.service.NodeIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class NodeInfoContributor implements InfoContributor {

    private final NodeIdentityService nodeIdentityService;

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("node", Map.of(
            "id", nodeIdentityService.getNodeId(),
            "instanceUuid", nodeIdentityService.getInstanceUuid(),
            "startedAt", nodeIdentityService.getStartedAt(),
            "profiles", nodeIdentityService.getActiveProfiles()
        ));
    }
}