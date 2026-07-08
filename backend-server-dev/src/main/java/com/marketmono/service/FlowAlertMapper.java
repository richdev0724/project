package com.marketmono.service;

import com.marketmono.dto.UnusualWhalesFlowAlertDto;
import com.marketmono.entity.DataSourceEnum;
import com.marketmono.entity.FlowAlert;
import com.marketmono.entity.OptionTypeEnum;
import com.marketmono.entity.AlertRule;
import com.marketmono.entity.Sector;
import com.marketmono.repository.SectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Utility class for mapping UnusualWhalesFlowAlertDto to FlowAlert entity
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlowAlertMapper {

    private final SectorRepository sectorRepository;

    /**
     * Map DTO from upstream market data API to Entity
     */
    public FlowAlert mapToEntity(UnusualWhalesFlowAlertDto dto, DataSourceEnum dataSource) {
        FlowAlert flowAlert = new FlowAlert();

        flowAlert.setAlertId(UUID.fromString(dto.id()));  // Parse UUID from API string
        flowAlert.setTicker(dto.ticker());
        flowAlert.setOptionSymbol(dto.optionChain());
        flowAlert.setStrike(dto.strike());
        flowAlert.setExpiryDate(dto.expiry());
        flowAlert.setDataSource(dataSource);

        // Convert string type to OptionTypeEnum
        String typeStr = dto.type() != null ? dto.type().toUpperCase() : "CALL";
        flowAlert.setOptionType(OptionTypeEnum.valueOf(typeStr));

        flowAlert.setPrice(dto.price());
        flowAlert.setBid(dto.bid());
        flowAlert.setAsk(dto.ask());
        flowAlert.setUnderlyingPrice(dto.underlyingPrice());
        flowAlert.setTotalSize(dto.totalSize());
        flowAlert.setTotalPremium(dto.totalPremium());
        flowAlert.setTradeCount(dto.tradeCount());
        flowAlert.setVolume(dto.volume());
        flowAlert.setOpenInterest(dto.openInterest());
        flowAlert.setVolumeOiRatio(dto.volumeOiRatio());
        flowAlert.setImpliedVolatilityStart(dto.ivStart());
        flowAlert.setImpliedVolatilityEnd(dto.ivEnd());

        // Convert string alertRule to AlertRule if present
        if (dto.alertRule() != null && !dto.alertRule().isEmpty()) {
            try {
                // Convert CamelCase to UPPERCASE_WITH_UNDERSCORES
                String enumName = convertAlertRuleNameToEnum(dto.alertRule());
                flowAlert.setAlertRule(AlertRule.valueOf(enumName));
            } catch (IllegalArgumentException _) {
                log.warn("Unknown alert rule: {}, skipping", dto.alertRule());
                // Leave as null if unknown value
            }
        }

        flowAlert.setHasSweep(dto.hasSweep());
        flowAlert.setHasFloor(dto.hasFloor());
        flowAlert.setHasSingleleg(dto.hasSingleleg());
        flowAlert.setHasMultileg(dto.hasMultileg());
        flowAlert.setAllOpeningTrades(dto.allOpeningTrades());

        // Lookup sector by name if present, or create if doesn't exist
        if (dto.sector() != null && !dto.sector().isEmpty()) {
            Optional<Sector> sector = sectorRepository.findBySectorName(dto.sector());
            if (sector.isPresent()) {
                flowAlert.setSector(sector.get());
            } else {
                // Create new sector if it doesn't exist
                Sector newSector = new Sector(dto.sector(), null);
                Sector savedSector = sectorRepository.save(newSector);
                flowAlert.setSector(savedSector);
                log.debug("Created new sector: {}", dto.sector());
            }
        }

        // Convert BigDecimal marketCap to Long (API returns decimal like "377.681157")
        flowAlert.setMarketCap(dto.marketCap() != null ? dto.marketCap().longValue() : null);
        flowAlert.setNextEarningsDate(dto.nextEarningsDate());
        flowAlert.setTradeStartTime(resolveTradeStartTime(dto));
        flowAlert.setTradeEndTime(dto.endTime() != null ? Instant.ofEpochMilli(dto.endTime()) : null);
        flowAlert.setTotalAskSidePremium(dto.totalAskSidePremium());
        flowAlert.setTotalBidSidePremium(dto.totalBidSidePremium());

        return flowAlert;
    }

    /**
     * Convert CamelCase alert rule name from API to UPPERCASE_WITH_UNDERSCORES enum format
     * Examples: RepeatedHits -> REPEATED_HITS, RepeatedHitsAscendingFill -> REPEATED_HITS_ASCENDING_FILL
     */
    private String convertAlertRuleNameToEnum(String camelCaseName) {
        if (camelCaseName == null || camelCaseName.isEmpty()) {
            return camelCaseName;
        }

        // Insert underscore before uppercase letters and convert to uppercase
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camelCaseName.length(); i++) {
            char c = camelCaseName.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                sb.append('_');
            }
            sb.append(Character.toUpperCase(c));
        }
        return sb.toString();
    }

    private Instant resolveTradeStartTime(UnusualWhalesFlowAlertDto dto) {
        if (dto.startTime() != null) {
            return Instant.ofEpochMilli(dto.startTime());
        }
        if (dto.createdAt() != null) {
            return dto.createdAt();
        }
        throw new IllegalArgumentException("Flow alert " + dto.id() + " missing both start_time and created_at");
    }
}
