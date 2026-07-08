package com.marketmono.controller;

import com.marketmono.dto.EconEventDto;
import com.marketmono.service.EconEventsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class EconEventsController {

    private final EconEventsService econEventsService;

    @GetMapping("/econ_events")
    public ResponseEntity<Map<String, List<EconEventDto>>> getEconEvents(
            @RequestParam(name = "start_date", required = false) String startDate,
            @RequestParam(name = "end_date", required = false) String endDate
    ) {
        log.info("GET /api/v1/econ_events - start_date: {}, end_date: {}", startDate, endDate);
        Map<String, List<EconEventDto>> response = econEventsService.getEconEvents(startDate, endDate);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }
}
