package com.marketmono.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Custom deserializer for UnusualWhalesFlowAlertDto that extracts strike price
 * from the option_chain field if not directly provided
 */
public class FlowAlertDeserializer extends JsonDeserializer<UnusualWhalesFlowAlertDto> {
    
    private static final Logger log = LoggerFactory.getLogger(FlowAlertDeserializer.class);
    private static final ObjectMapper PLAIN_TREE_MAPPER = new ObjectMapper();
    
    @Override
    public UnusualWhalesFlowAlertDto deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        
        // Redis cache deserialization uses an ObjectMapper with default typing enabled.
        // Reading this custom DTO as a tree through that mapper makes Jackson require
        // nested JsonNode @class metadata, so use a plain mapper for tree materialization.
        JsonNode node = PLAIN_TREE_MAPPER.readTree(p);
        
        // Extract fields with defaults
        String id = getStringOrNull(node, "id");
        String ticker = getStringOrNull(node, "ticker");
        String optionChain = getStringOrNull(node, "option_chain");
        
        // Extract or compute strike price
        BigDecimal strike = getBigDecimalOrNull(node, "strike");
        if (strike == null && optionChain != null) {
            // Extract strike from option chain (e.g., "ASTS251031C00080000" -> 80.00)
            strike = UnusualWhalesFlowAlertDto.extractStrikeFromOptionChain(optionChain);
        }
        
        LocalDate expiry = getLocalDateOrNull(node, "expiry");
        if (expiry == null && optionChain != null && optionChain.length() >= 15) {
            // Try to extract expiry from option chain
            // Format: TICKER(1-6) + YYMMDD(6) + C/P(1) + STRIKE(8)
            // Example: "SPY251103C00683000" = SPY + 251103 + C + 00683000
            // Work backwards: strike (8) + type (1) = last 9 chars
            try {
                // Position of type (C/P) is always 9 chars from end
                int typePos = optionChain.length() - 9;
                char typeChar = optionChain.charAt(typePos);
                
                // Verify it's actually a C or P
                if ((typeChar == 'C' || typeChar == 'P') && typePos >= 6) {
                    // Expiry is the 6 chars before the type (positions: typePos-6 to typePos-1)
                    String expiryStr = optionChain.substring(typePos - 6, typePos);
                    if (expiryStr.length() == 6 && expiryStr.matches("\\d{6}")) {
                        int year = 2000 + Integer.parseInt(expiryStr.substring(0, 2));
                        int month = Integer.parseInt(expiryStr.substring(2, 4));
                        int day = Integer.parseInt(expiryStr.substring(4, 6));
                        expiry = LocalDate.of(year, month, day);
                    }
                }
            } catch (Exception e) {
                log.debug("Could not extract expiry from option chain: {}", optionChain, e);
            }
        }
        
        String type = getStringOrNull(node, "option_type");
        if (type == null && optionChain != null && optionChain.length() >= 2) {
            // Extract type from option chain (C = CALL, P = PUT)
            char typeChar = optionChain.charAt(optionChain.length() - 9);
            type = typeChar == 'C' ? "CALL" : typeChar == 'P' ? "PUT" : null;
        }
        
        return new UnusualWhalesFlowAlertDto(
            id,
            ticker,
            optionChain,
            strike,
            expiry,
            type,
            getBigDecimalOrNull(node, "price"),
            getBigDecimalOrNull(node, "bid"),
            getBigDecimalOrNull(node, "ask"),
            getBigDecimalOrNull(node, "underlying_price"),
            getIntegerOrNull(node, "total_size"),
            getBigDecimalOrNull(node, "total_premium"),
            getIntegerOrNull(node, "trade_count"),
            getIntegerOrNull(node, "volume"),
            getIntegerOrNull(node, "open_interest"),
            getBigDecimalOrNull(node, "volume_oi_ratio"),
            getBigDecimalOrNull(node, "iv_start"),
            getBigDecimalOrNull(node, "iv_end"),
            getStringOrNull(node, "alert_rule"),
            getBooleanOrNull(node, "has_sweep"),
            getBooleanOrNull(node, "has_floor"),
            getBooleanOrNull(node, "has_singleleg"),
            getBooleanOrNull(node, "has_multileg"),
            getBooleanOrNull(node, "all_opening_trades"),
            getStringOrNull(node, "sector"),
            getBigDecimalOrNull(node, "marketcap"),
            getLocalDateOrNull(node, "next_earnings_date"),
            getLongOrNull(node, "start_time"),
            getLongOrNull(node, "end_time"),
            getBigDecimalOrNull(node, "total_ask_side_prem"),
            getBigDecimalOrNull(node, "total_bid_side_prem"),
            getInstantOrNull(node, "created_at"),
            getStringOrNull(node, "rule_id"),
            getIntegerOrNull(node, "expiry_count"),
            getStringOrNull(node, "er_time"),
            getStringOrNull(node, "issue_type"),
            getBooleanOrNull(node, "missing_periscope")
        );
    }

    private JsonNode getFieldOrNull(JsonNode node, String fieldName) {
        if (!node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }

        JsonNode value = node.get(fieldName);
        if (value.isArray() && value.size() == 2 && value.get(0).isTextual()) {
            JsonNode typedValue = value.get(1);
            return typedValue == null || typedValue.isNull() ? null : typedValue;
        }
        return value;
    }
    
    private String getStringOrNull(JsonNode node, String fieldName) {
        JsonNode value = getFieldOrNull(node, fieldName);
        return value == null ? null : value.asText();
    }
    
    private BigDecimal getBigDecimalOrNull(JsonNode node, String fieldName) {
        JsonNode value = getFieldOrNull(node, fieldName);
        if (value != null) {
            try {
                return new BigDecimal(value.asText());
            } catch (NumberFormatException e) {
                log.debug("Could not parse {} as BigDecimal: {}", fieldName, value.asText());
            }
        }
        return null;
    }
    
    private Integer getIntegerOrNull(JsonNode node, String fieldName) {
        JsonNode value = getFieldOrNull(node, fieldName);
        if (value != null) {
            try {
                return value.asInt();
            } catch (NumberFormatException e) {
                log.debug("Could not parse {} as Integer: {}", fieldName, value.asText());
            }
        }
        return null;
    }
    
    private Long getLongOrNull(JsonNode node, String fieldName) {
        JsonNode value = getFieldOrNull(node, fieldName);
        if (value != null) {
            try {
                return value.asLong();
            } catch (NumberFormatException e) {
                log.debug("Could not parse {} as Long: {}", fieldName, value.asText());
            }
        }
        return null;
    }
    
    private Boolean getBooleanOrNull(JsonNode node, String fieldName) {
        JsonNode value = getFieldOrNull(node, fieldName);
        return value == null ? null : value.asBoolean();
    }
    
    private LocalDate getLocalDateOrNull(JsonNode node, String fieldName) {
        JsonNode value = getFieldOrNull(node, fieldName);
        if (value != null) {
            try {
                if (value.isArray() && value.size() >= 3) {
                    return LocalDate.of(value.get(0).asInt(), value.get(1).asInt(), value.get(2).asInt());
                }
                return LocalDate.parse(value.asText(), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception e) {
                log.debug("Could not parse {} as LocalDate: {}", fieldName, value.asText());
            }
        }
        return null;
    }
    
    private Instant getInstantOrNull(JsonNode node, String fieldName) {
        JsonNode value = getFieldOrNull(node, fieldName);
        if (value != null) {
            try {
                if (value.isArray() && value.size() >= 2) {
                    return Instant.ofEpochSecond(value.get(0).asLong(), value.get(1).asLong());
                }
                if (value.isNumber()) {
                    return instantFromEpochSeconds(value.decimalValue());
                }
                return Instant.parse(value.asText());
            } catch (Exception e) {
                try {
                    return instantFromEpochSeconds(new BigDecimal(value.asText()));
                } catch (Exception ignored) {
                    log.debug("Could not parse {} as Instant: {}", fieldName, value.asText());
                }
            }
        }
        return null;
    }

    private Instant instantFromEpochSeconds(BigDecimal epochSeconds) {
        long seconds = epochSeconds.longValue();
        long nanos = epochSeconds.subtract(BigDecimal.valueOf(seconds)).movePointRight(9).longValue();
        return Instant.ofEpochSecond(seconds, nanos);
    }
}
