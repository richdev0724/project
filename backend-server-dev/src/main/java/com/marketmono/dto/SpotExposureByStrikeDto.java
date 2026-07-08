package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Spot exposure data aggregated by strike price
 * Maps to /api/stock/{ticker}/spot-exposures/strike and expiry-strike responses
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SpotExposureByStrikeDto(
    String ticker,
    
    @JsonProperty("put_delta_oi")
    BigDecimal putDeltaOi,
    
    @JsonProperty("call_delta_vol")
    BigDecimal callDeltaVol,
    
    @JsonProperty("call_gamma_oi")
    BigDecimal callGammaOi,
    
    @JsonProperty("call_vanna_oi")
    BigDecimal callVannaOi,
    
    LocalDate date,
    
    @JsonProperty("put_vanna_vol")
    BigDecimal putVannaVol,
    
    @JsonProperty("put_charm_vol")
    BigDecimal putCharmVol,
    
    @JsonProperty("put_gamma_vol")
    BigDecimal putGammaVol,
    
    @JsonProperty("call_vanna_vol")
    BigDecimal callVannaVol,
    
    @JsonProperty("call_vanna_bid")
    BigDecimal callVannaBid,
    
    @JsonProperty("put_vanna_bid")
    BigDecimal putVannaBid,
    
    @JsonProperty("call_gamma_vol")
    BigDecimal callGammaVol,
    
    @JsonProperty("call_charm_bid")
    BigDecimal callCharmBid,
    
    @JsonProperty("put_delta_vol")
    BigDecimal putDeltaVol,
    
    OffsetDateTime time,
    
    @JsonProperty("put_gamma_oi")
    BigDecimal putGammaOi,
    
    @JsonProperty("put_charm_ask")
    BigDecimal putCharmAsk,
    
    @JsonProperty("call_charm_oi")
    BigDecimal callCharmOi,
    
    @JsonProperty("call_delta_oi")
    BigDecimal callDeltaOi,
    
    @JsonProperty("call_delta_bid")
    BigDecimal callDeltaBid,
    
    @JsonProperty("call_gamma_ask")
    BigDecimal callGammaAsk,
    
    @JsonProperty("call_charm_vol")
    BigDecimal callCharmVol,
    
    @JsonProperty("put_vanna_oi")
    BigDecimal putVannaOi,
    
    @JsonProperty("put_gamma_ask")
    BigDecimal putGammaAsk,
    
    BigDecimal price,
    
    @JsonProperty("put_delta_ask")
    BigDecimal putDeltaAsk,
    
    BigDecimal strike,
    
    @JsonProperty("call_charm_ask")
    BigDecimal callCharmAsk,
    
    @JsonProperty("put_delta_bid")
    BigDecimal putDeltaBid,
    
    @JsonProperty("call_gamma_bid")
    BigDecimal callGammaBid,
    
    @JsonProperty("put_vanna_ask")
    BigDecimal putVannaAsk,
    
    @JsonProperty("call_delta_ask")
    BigDecimal callDeltaAsk,
    
    @JsonProperty("call_vanna_ask")
    BigDecimal callVannaAsk,
    
    @JsonProperty("put_charm_bid")
    BigDecimal putCharmBid,
    
    @JsonProperty("put_charm_oi")
    BigDecimal putCharmOi,
    
    @JsonProperty("put_gamma_bid")
    BigDecimal putGammaBid,
    
    LocalDate expiry
) {}





