package com.marketmono.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmono.config.CrmPriceStreamProperties;
import com.marketmono.entity.UserFilter;
import com.marketmono.entity.UserFilterType;
import com.marketmono.entity.UserPreference;
import com.marketmono.entity.UserPreferenceCategory;
import com.marketmono.repository.UserFilterRepository;
import com.marketmono.repository.UserPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrataStockPriceTickerUniverseServiceTest {

    @Mock
    private CollectionConfigService collectionConfigService;

    @Mock
    private UserPreferenceRepository userPreferenceRepository;

    @Mock
    private UserFilterRepository userFilterRepository;

    private CrmPriceStreamProperties crmPriceStreamProperties;
    private StrataStockPriceTickerUniverseService service;

    @BeforeEach
    void setUp() {
        crmPriceStreamProperties = new CrmPriceStreamProperties();
        service = new StrataStockPriceTickerUniverseService(
            collectionConfigService,
            userPreferenceRepository,
            userFilterRepository,
            new ObjectMapper(),
            crmPriceStreamProperties
        );
    }

    @Test
    void shouldMergeFixedCollectionConfigAndUserPreferenceTickersWithSourceDeduping() {
        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of("SPY", "MU"));
        when(userPreferenceRepository.findByCategory(UserPreferenceCategory.DISPLAY))
            .thenReturn(List.of(displayPreference("""
                {
                  "monoStrata": {
                    "tickers": ["msft", "SPY"]
                  }
                }
                """)));
        when(userFilterRepository.findAll()).thenReturn(List.of());

        StrataStockPriceTickerUniverse universe = service.getTickerUniverse();

        assertThat(universe.displayTickers()).contains(
            "SPX", "VIX", "SPY", "QQQ", "IWM", "DIA", "GLD", "SLV",
            "META", "NVDA", "AAPL", "TSLA", "GOOG", "GOOGL", "AMZN", "MSFT", "NFLX",
            "SPXW", "VIXW"
        );
        assertThat(universe.sourceTickers()).contains("SPX", "VIX", "SPY", "MU", "MSFT");
        assertThat(universe.sourceTickers().stream().filter("SPX"::equals)).hasSize(1);
        assertThat(universe.provenance().fixedCount()).isEqualTo(19);
        assertThat(universe.provenance().collectionConfigCount()).isEqualTo(2);
        assertThat(universe.provenance().userPreferenceCount()).isEqualTo(2);
        assertThat(universe.provenance().crmSavedFilterCount()).isEqualTo(0);
    }

    @Test
    void shouldIncludeIndexAliasesInFixedTickerUniverse() {
        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of());
        when(userPreferenceRepository.findByCategory(UserPreferenceCategory.DISPLAY)).thenReturn(List.of());
        when(userFilterRepository.findAll()).thenReturn(List.of());

        StrataStockPriceTickerUniverse universe = service.getTickerUniverse();

        assertThat(universe.mappings()).contains(
            new StrataTickerMapping("SPX", "SPX"),
            new StrataTickerMapping("SPXW", "SPX"),
            new StrataTickerMapping("VIX", "VIX"),
            new StrataTickerMapping("VIXW", "VIX")
        );
        assertThat(universe.sourceTickers().stream().filter("SPX"::equals)).hasSize(1);
        assertThat(universe.sourceTickers().stream().filter("VIX"::equals)).hasSize(1);
    }

    @Test
    void shouldIncludeTickersSavedInCrmSavedFilters() {
        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of());
        when(userPreferenceRepository.findByCategory(UserPreferenceCategory.DISPLAY)).thenReturn(List.of());
        when(userFilterRepository.findAll())
            .thenReturn(List.of(
                savedFilter("""
                    {
                      "tickers": "pltr, avgo",
                      "ignoreTickers": "baba"
                    }
                    """),
                savedFilter("""
                    {
                      "ticker": "",
                      "tickers": ["SHOP", "SPXW"]
                    }
                    """)
            ));

        StrataStockPriceTickerUniverse universe = service.getTickerUniverse();

        assertThat(universe.displayTickers()).contains("PLTR", "AVGO", "SHOP", "SPXW");
        assertThat(universe.displayTickers()).doesNotContain("BABA");
        assertThat(universe.sourceTickers()).contains("PLTR", "AVGO", "SHOP", "SPX");
        assertThat(universe.provenance().crmSavedFilterCount()).isEqualTo(4);
    }

    @Test
    void shouldExcludeCrmTickersFromRestUniverseWhenPriceStreamEnabled() {
        crmPriceStreamProperties.setEnabled(true);
        when(collectionConfigService.getSpotGexTickers()).thenReturn(List.of());
        when(userPreferenceRepository.findByCategory(UserPreferenceCategory.DISPLAY)).thenReturn(List.of());
        when(userFilterRepository.findAll())
            .thenReturn(List.of(
                savedFilter("""
                    {
                      "tickers": ["PLTR", "AAPL", "SPXW"]
                    }
                    """)
            ));

        StrataStockPriceTickerUniverse universe = service.getTickerUniverse();

        // Streamed CRM tickers leave the REST universe, even fixed ones like AAPL.
        assertThat(universe.displayTickers()).doesNotContain("PLTR", "AAPL");
        // Index tickers always keep the REST pipeline.
        assertThat(universe.displayTickers()).contains("SPX", "SPXW", "VIX", "VIXW");
        assertThat(universe.provenance().crmSavedFilterCount()).isEqualTo(0);
    }

    @Test
    void shouldReturnCrmPriceStreamTickersWithoutIndexTickers() {
        when(userFilterRepository.findAll())
            .thenReturn(List.of(
                savedFilter("""
                    {
                      "tickers": "pltr, spxw, vix",
                      "symbol": "AAPL"
                    }
                    """)
            ));

        assertThat(service.getCrmPriceStreamTickers()).containsExactly("PLTR", "AAPL");
    }

    private static UserPreference displayPreference(String json) {
        UserPreference preference = new UserPreference();
        preference.setCategory(UserPreferenceCategory.DISPLAY);
        preference.setPreferencesJson(json);
        return preference;
    }

    private static UserFilter savedFilter(String filterQuery) {
        UserFilter filter = new UserFilter();
        filter.setFilterType(UserFilterType.FLOW);
        filter.setFilterQuery(filterQuery);
        return filter;
    }
}
