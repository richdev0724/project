package com.marketmono.controller;

import com.marketmono.dto.PublicChangelogReleaseDto;
import com.marketmono.service.ChangelogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public controller for Help-page changelog data.
 */
@RestController
@RequestMapping("/api/v1/changelog")
@RequiredArgsConstructor
@Slf4j
public class ChangelogController {

    private final ChangelogService changelogService;

    @GetMapping
    public ResponseEntity<List<PublicChangelogReleaseDto>> getChangelog() {
        log.debug("GET /api/v1/changelog");
        return ResponseEntity.ok(changelogService.getPublicChangelog());
    }
}