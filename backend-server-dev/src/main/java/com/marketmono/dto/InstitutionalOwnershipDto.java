package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InstitutionalOwnershipDto(
    @JsonProperty("avg_price")
    String avgPrice,

    String cik,

    @JsonProperty("filing_date")
    String filingDate,

    @JsonProperty("first_buy")
    String firstBuy,

    @JsonProperty("historical_units")
    List<Long> historicalUnits,

    @JsonProperty("inst_share_value")
    String instShareValue,

    @JsonProperty("inst_value")
    String instValue,

    String name,

    List<String> people,

    @JsonProperty("report_date")
    String reportDate,

    @JsonProperty("shares_outstanding")
    String sharesOutstanding,

    @JsonProperty("short_name")
    String shortName,

    List<String> tags,

    Long units,

    @JsonProperty("units_changed")
    Long unitsChanged,

    Long value
) {}
