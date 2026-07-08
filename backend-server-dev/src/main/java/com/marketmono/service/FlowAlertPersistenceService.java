package com.marketmono.service;

import com.marketmono.dto.UnusualWhalesFlowAlertDto;
import com.marketmono.entity.DataSourceEnum;
import com.marketmono.entity.FlowAlert;
import com.marketmono.repository.FlowAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Async service for persisting flow alerts to database
 * Separated from FlowAlertService to avoid circular dependencies
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlowAlertPersistenceService {

    private final FlowAlertRepository flowAlertRepository;
    private final FlowAlertMapper flowAlertMapper;

    /**
     * Save flow alert to database asynchronously
     */
    @Async
    public void saveFlowAlertAsync(UnusualWhalesFlowAlertDto dto) {
        try {
            FlowAlert flowAlert = flowAlertMapper.mapToEntity(dto, DataSourceEnum.WEBSOCKET);
            // Check the full Timescale-compatible composite key.
            if (flowAlertRepository.existsById(flowAlert.getId())) {
                log.trace("Skipping duplicate flow alert from WS: {}", dto.id());
                return;
            }

            flowAlertRepository.save(flowAlert);

            log.debug("[WS] Saved - {}: {} | {} | {}",
                String.format("%-5s", dto.ticker()),
                String.format("%6s", dto.strike()),
                dto.expiry(),
                dto.id());
        } catch (Exception e) {
            log.error("Error saving WS flow alert {}: {}", dto.id(), e.getMessage(), e);
        }
    }
}