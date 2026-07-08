package com.marketmono.controller;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.OptionContractsResponse;
import com.marketmono.service.UnusualWhalesQueryParamMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScreenerControllerTest {

    @Mock
    private UnusualWhalesClient unusualWhalesClient;

    @Mock
    private UnusualWhalesQueryParamMapper unusualWhalesQueryParamMapper;

    @InjectMocks
    private ScreenerController screenerController;

    @Test
    void shouldUppercaseTickerAndForwardAllScreenerParams() {
        MultiValueMap<String, String> requestParams = new LinkedMultiValueMap<>();
        requestParams.add("ticker_symbol", "nvda");
        requestParams.add("is_otm", "true");
        requestParams.add("max_multileg_volume_ratio", "0.2");
        requestParams.add("min_premium", "100000");
        requestParams.add("vol_greater_oi", "true");
        requestParams.add("min_skew_perc", "0.65");
        requestParams.add("order", "stock_price");
        requestParams.add("order_direction", "desc");
        requestParams.add("limit", "200");

        Map<String, Object> mappedParams = new LinkedHashMap<>();
        mappedParams.put("ticker_symbol", "NVDA");
        mappedParams.put("is_otm", "true");
        mappedParams.put("max_multileg_volume_ratio", "0.2");
        mappedParams.put("min_premium", "100000");
        mappedParams.put("vol_greater_oi", "true");
        mappedParams.put("min_skew_perc", "0.65");
        mappedParams.put("order", "stock_price");
        mappedParams.put("order_direction", "desc");
        mappedParams.put("limit", "200");

        OptionContractsResponse upstreamResponse = new OptionContractsResponse(List.of());
        when(unusualWhalesQueryParamMapper.toParams(org.mockito.ArgumentMatchers.<MultiValueMap<String, String>>any()))
            .thenReturn(mappedParams);
        when(unusualWhalesClient.getOptionContracts(mappedParams)).thenReturn(upstreamResponse);

        ResponseEntity<OptionContractsResponse> response = screenerController.getOptionContracts(requestParams);

        verify(unusualWhalesQueryParamMapper).toParams(argThat((MultiValueMap<String, String> params) ->
            "NVDA".equals(params.getFirst("ticker_symbol"))
                && "true".equals(params.getFirst("is_otm"))
                && "0.2".equals(params.getFirst("max_multileg_volume_ratio"))
                && "100000".equals(params.getFirst("min_premium"))
                && "true".equals(params.getFirst("vol_greater_oi"))
                && "0.65".equals(params.getFirst("min_skew_perc"))
                && "stock_price".equals(params.getFirst("order"))
                && "desc".equals(params.getFirst("order_direction"))
                && "200".equals(params.getFirst("limit"))
        ));
        verify(unusualWhalesClient).getOptionContracts(mappedParams);
        assertThat(response.getBody()).isEqualTo(upstreamResponse);
    }
}
