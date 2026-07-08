package com.marketmono.controller;

import com.marketmono.dto.NodeStatusResponse;
import com.marketmono.service.NodeStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/node/status")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Node Status", description = "Per-node diagnostic and active-active runtime status")
public class NodeStatusController {

    private final NodeStatusService nodeStatusService;

    @GetMapping
    @Operation(summary = "Get current node diagnostic status")
    public ResponseEntity<NodeStatusResponse> getNodeStatus() {
        log.debug("GET /api/v1/node/status");
        return ResponseEntity.ok(nodeStatusService.getStatus());
    }
}