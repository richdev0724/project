package com.marketmono.service.kafka;

import com.marketmono.entity.DataSourceEnum;
import com.marketmono.entity.FlowAlert;
import com.marketmono.entity.AlertRule;
import com.marketmono.entity.OptionTypeEnum;
import com.marketmono.proto.client.FlowAlertProto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps protobuf FlowAlert messages from Kafka to existing FlowAlert JPA entity.
 */
@Component
@Slf4j
public class FlowAlertProtoMapper {

    // OSI option symbol pattern: TICKER YYMMDD C/P STRIKE (e.g., SPY241227C00480000)
    private static final Pattern OSI_PATTERN = Pattern.compile(
        "^([A-Z]+)(\\d{6})([CP])(\\d{8})$"
    );

    public FlowAlert toEntity(FlowAlertProto.FlowAlert proto) {
        log.trace("[KAFKA] Mapping flow alert: id={}, ticker={}, option={}",
            proto.getId(), proto.getTicker(), proto.getOptionChain());

        FlowAlert entity = new FlowAlert();

        entity.setAlertId(UUID.fromString(proto.getId()));
        entity.setTicker(proto.getTicker());
        entity.setOptionSymbol(proto.getOptionChain());
        entity.setDataSource(DataSourceEnum.KAFKA);

        // Parse OSI symbol for strike, expiry, option type
        parseOsiSymbol(proto.getOptionChain(), entity);

        entity.setPrice(BigDecimal.valueOf(proto.getPrice()));
        entity.setBid(BigDecimal.valueOf(proto.getBid()));
        entity.setAsk(BigDecimal.valueOf(proto.getAsk()));
        entity.setUnderlyingPrice(BigDecimal.valueOf(proto.getUnderlyingPrice()));
        entity.setTotalSize(proto.getTotalSize());
        entity.setTotalPremium(BigDecimal.valueOf(proto.getTotalPremium()));
        entity.setTradeCount(proto.getTradeCount());
        entity.setVolume(proto.getVolume());
        entity.setOpenInterest(proto.getOpenInterest());
        entity.setVolumeOiRatio(BigDecimal.valueOf(proto.getVolumeOiRatio()));

        // Alert rule from rule_id
        if (!proto.getRuleId().isEmpty()) {
            try {
                String enumName = convertAlertRuleNameToEnum(proto.getRuleId());
                entity.setAlertRule(AlertRule.valueOf(enumName));
            } catch (IllegalArgumentException _) {
                log.warn("Unknown alert rule from Kafka: {}", proto.getRuleId());
            }
        }

        entity.setHasSweep(proto.getHasSweep());
        entity.setHasFloor(proto.getHasFloor());
        entity.setHasSingleleg(proto.getHasSingleleg());
        entity.setHasMultileg(proto.getHasMultileg());
        entity.setAllOpeningTrades(proto.getAllOpeningTrades());

        entity.setTradeStartTime(Instant.ofEpochMilli(proto.getStartTime()));
        entity.setTradeEndTime(proto.getEndTime() != 0 ? Instant.ofEpochMilli(proto.getEndTime()) : null);
        entity.setTotalAskSidePremium(BigDecimal.valueOf(proto.getTotalAskSidePrem()));
        entity.setTotalBidSidePremium(BigDecimal.valueOf(proto.getTotalBidSidePrem()));

        log.debug("[KAFKA] Mapped flow alert: id={}, ticker={}, optionType={}, strike={}, expiry={}, rule={}",
            entity.getAlertId(), entity.getTicker(), entity.getOptionType(),
            entity.getStrike(), entity.getExpiryDate(), entity.getAlertRule());

        return entity;
    }

    private void parseOsiSymbol(String osi, FlowAlert entity) {
        if (osi == null || osi.isEmpty()) {
            log.warn("[KAFKA] Empty OSI symbol for flow alert: {}", entity.getAlertId());
            return;
        }

        Matcher m = OSI_PATTERN.matcher(osi);
        if (m.matches()) {
            // Expiry: YYMMDD
            String dateStr = m.group(2);
            int year = 2000 + Integer.parseInt(dateStr.substring(0, 2));
            int month = Integer.parseInt(dateStr.substring(2, 4));
            int day = Integer.parseInt(dateStr.substring(4, 6));
            entity.setExpiryDate(LocalDate.of(year, month, day));

            // Option type: C or P
            entity.setOptionType("C".equals(m.group(3)) ? OptionTypeEnum.CALL : OptionTypeEnum.PUT);

            // Strike: scaled by 1000 (e.g., 00480000 = $480.000)
            long strikeScaled = Long.parseLong(m.group(4));
            entity.setStrike(BigDecimal.valueOf(strikeScaled, 3));

            log.trace("[KAFKA] Parsed OSI symbol: {} → ticker={}, expiry={}, type={}, strike={}",
                osi, m.group(1), entity.getExpiryDate(), entity.getOptionType(), entity.getStrike());
        } else {
            log.warn("Could not parse OSI symbol: {}", osi);
        }
    }

    private String convertAlertRuleNameToEnum(String camelCaseName) {
        if (camelCaseName == null || camelCaseName.isEmpty()) return camelCaseName;
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
}
