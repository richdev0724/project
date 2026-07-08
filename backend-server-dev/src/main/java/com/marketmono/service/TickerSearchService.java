package com.marketmono.service;

import com.marketmono.client.FmpClient;
import com.marketmono.dto.TickerSearchResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TickerSearchService {

    private final FmpClient fmpClient;

    public List<TickerSearchResultDto> searchTickers(String query) {
        return fmpClient.searchSymbols(query);
    }

    public List<TickerSearchResultDto> searchTickersByCompany(String query) {
        return fmpClient.searchByCompanyName(query);
    }
}
