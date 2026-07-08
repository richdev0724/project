package com.marketmono.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmono.config.CrmPriceStreamProperties;
import com.marketmono.entity.UserFilter;
import com.marketmono.entity.UserPreference;
import com.marketmono.entity.UserPreferenceCategory;
import com.marketmono.repository.UserFilterRepository;
import com.marketmono.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class StrataStockPriceTickerUniverseService {

    private static final List<String> FIXED_DISPLAY_TICKERS = List.of(
        "SPX", "SPXW", "VIX", "VIXW", "SPY", "QQQ", "IWM", "DIA", "GLD", "SLV", "META",
        "NVDA", "AAPL", "TSLA", "GOOG", "GOOGL", "AMZN", "MSFT", "NFLX"
    );
    private static final List<String> CRM_TICKER_FIELDS = List.of(
        "ticker", "tickers", "symbol", "symbols", "underlying", "underlyings"
    );
    /** Index tickers that always keep the REST stock-state pipeline. */
    private static final Set<String> PRICE_STREAM_EXCLUDED_TICKERS = Set.of("SPX", "SPXW", "VIX", "VIXW");
    private static final int MAX_CRM_FILTER_QUERY_CHARS = 20_000;

    private final CollectionConfigService collectionConfigService;
    private final UserPreferenceRepository userPreferenceRepository;
    private final UserFilterRepository userFilterRepository;
    private final ObjectMapper objectMapper;
    private final CrmPriceStreamProperties crmPriceStreamProperties;

    /**
     * Normalized display tickers saved in CRM filters that are served by the
     * Unusual Whales price websocket instead of stock-state REST polling.
     * SPX/SPXW/VIX/VIXW are never streamed.
     */
    public Set<String> getCrmPriceStreamTickers() {
        Set<String> tickers = new LinkedHashSet<>();
        for (String ticker : readCrmSavedFilterTickers()) {
            try {
                String displayTicker = StrataTickerPolicy.normalize(ticker);
                if (!PRICE_STREAM_EXCLUDED_TICKERS.contains(displayTicker)) {
                    tickers.add(displayTicker);
                }
            } catch (IllegalArgumentException ex) {
                log.debug("[CRM-PRICE-STREAM] Ignoring invalid CRM ticker '{}': {}", ticker, ex.getMessage());
            }
        }
        return tickers;
    }

    public StrataStockPriceTickerUniverse getTickerUniverse() {
        List<String> collectionTickers = safeCollectionConfigTickers();
        List<String> userPreferenceTickers = readUserPreferenceTickers();
        boolean crmStreamEnabled = crmPriceStreamProperties.isEnabled();
        List<String> crmSavedFilterTickers = crmStreamEnabled ? List.of() : readCrmSavedFilterTickers();
        Set<String> streamedTickers = crmStreamEnabled ? getCrmPriceStreamTickers() : Set.of();

        Map<String, StrataTickerMapping> mappingsByDisplayTicker = new LinkedHashMap<>();
        addMappings(mappingsByDisplayTicker, FIXED_DISPLAY_TICKERS, streamedTickers);
        addMappings(mappingsByDisplayTicker, collectionTickers, streamedTickers);
        addMappings(mappingsByDisplayTicker, userPreferenceTickers, streamedTickers);
        addMappings(mappingsByDisplayTicker, crmSavedFilterTickers, streamedTickers);

        StrataTickerUniverseProvenance provenance = new StrataTickerUniverseProvenance(
            FIXED_DISPLAY_TICKERS.size(),
            collectionTickers.size(),
            userPreferenceTickers.size(),
            crmSavedFilterTickers.size()
        );
        StrataStockPriceTickerUniverse universe = new StrataStockPriceTickerUniverse(
            new ArrayList<>(mappingsByDisplayTicker.values()),
            provenance
        );

        log.info(
            "[STRATA-STOCK-PRICE] Ticker universe: fixed={}, collectionConfig={}, userPreference={}, crmSavedFilter={}, displayTotal={}, uniqueSourceTotal={}",
            provenance.fixedCount(),
            provenance.collectionConfigCount(),
            provenance.userPreferenceCount(),
            provenance.crmSavedFilterCount(),
            universe.displayTickers().size(),
            universe.sourceTickers().size()
        );
        return universe;
    }

    private List<String> safeCollectionConfigTickers() {
        try {
            return collectionConfigService.getSpotGexTickers();
        } catch (RuntimeException ex) {
            log.warn("[STRATA-STOCK-PRICE] Failed to read collection_config Spot GEX tickers: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<String> readUserPreferenceTickers() {
        return userPreferenceRepository.findByCategory(UserPreferenceCategory.DISPLAY).stream()
            .flatMap(preference -> extractMonoStrataTickers(preference).stream())
            .toList();
    }

    private List<String> readCrmSavedFilterTickers() {
        try {
            return userFilterRepository.findAll().stream()
                .flatMap(filter -> extractCrmSavedFilterTickers(filter).stream())
                .toList();
        } catch (RuntimeException ex) {
            log.warn("[STRATA-STOCK-PRICE] Failed to read CRM saved-filter tickers: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<String> extractMonoStrataTickers(UserPreference preference) {
        try {
            JsonNode root = objectMapper.readTree(preference.getPreferencesJson());
            JsonNode tickers = root.path("monoStrata").path("tickers");
            if (!tickers.isArray()) {
                return List.of();
            }

            List<String> values = new ArrayList<>();
            tickers.forEach(ticker -> {
                if (ticker.isTextual()) {
                    values.add(ticker.asText());
                }
            });
            return values;
        } catch (RuntimeException ex) {
            log.debug("[STRATA-STOCK-PRICE] Skipping malformed DISPLAY preference id={}: {}",
                preference.getId(), ex.getMessage());
            return List.of();
        } catch (Exception ex) {
            log.debug("[STRATA-STOCK-PRICE] Skipping unreadable DISPLAY preference id={}: {}",
                preference.getId(), ex.getMessage());
            return List.of();
        }
    }

    private List<String> extractCrmSavedFilterTickers(UserFilter filter) {
        String filterQuery = filter.getFilterQuery();
        if (filterQuery == null || filterQuery.isBlank()) {
            return List.of();
        }
        if (filterQuery.length() > MAX_CRM_FILTER_QUERY_CHARS) {
            log.debug("[STRATA-STOCK-PRICE] Skipping oversized CRM saved filter id={}", filter.getId());
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(filterQuery);
            List<String> values = new ArrayList<>();
            for (String fieldName : CRM_TICKER_FIELDS) {
                collectTickerValues(root.path(fieldName), values);
            }
            return values;
        } catch (RuntimeException ex) {
            log.debug("[STRATA-STOCK-PRICE] Skipping malformed CRM saved filter id={}: {}",
                filter.getId(), ex.getMessage());
            return List.of();
        } catch (Exception ex) {
            log.debug("[STRATA-STOCK-PRICE] Skipping unreadable CRM saved filter id={}: {}",
                filter.getId(), ex.getMessage());
            return List.of();
        }
    }

    private void collectTickerValues(JsonNode node, List<String> values) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            addDelimitedTickerText(node.asText(), values);
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collectTickerValues(item, values));
        }
    }

    private void addDelimitedTickerText(String text, List<String> values) {
        if (text == null || text.isBlank()) {
            return;
        }

        int tokenStart = -1;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (isTickerDelimiter(current)) {
                appendTickerToken(text, tokenStart, index, values);
                tokenStart = -1;
            } else if (tokenStart < 0) {
                tokenStart = index;
            }
        }
        appendTickerToken(text, tokenStart, text.length(), values);
    }

    private boolean isTickerDelimiter(char value) {
        return value == ',' || value == ';' || Character.isWhitespace(value);
    }

    private void appendTickerToken(String text, int startInclusive, int endExclusive, List<String> values) {
        if (startInclusive < 0) {
            return;
        }
        String value = text.substring(startInclusive, endExclusive).trim();
        if (!value.isBlank()) {
            values.add(value);
        }
    }

    private void addMappings(
        Map<String, StrataTickerMapping> mappingsByDisplayTicker,
        List<String> tickers,
        Set<String> streamedTickers
    ) {
        for (String ticker : tickers) {
            try {
                StrataTickerMapping mapping = StrataTickerPolicy.resolve(ticker);
                if (streamedTickers.contains(mapping.displayTicker())) {
                    continue;
                }
                mappingsByDisplayTicker.putIfAbsent(mapping.displayTicker(), mapping);
            } catch (IllegalArgumentException ex) {
                log.debug("[STRATA-STOCK-PRICE] Ignoring invalid ticker '{}': {}", ticker, ex.getMessage());
            }
        }
    }
}
