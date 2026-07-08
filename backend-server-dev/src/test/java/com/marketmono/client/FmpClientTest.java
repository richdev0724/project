package com.marketmono.client;

import com.marketmono.dto.CompanyProfileDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FmpClientTest {

    @Test
    void shouldUseStableCompanyProfileEndpointAndMapStableFields() {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
            .exchangeFunction(request -> {
                requestedUri.set(request.url());
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("""
                        [
                          {
                            "symbol": "NVDA",
                            "companyName": "NVIDIA Corporation",
                            "description": "Accelerated computing company",
                            "industry": "Semiconductors",
                            "sector": "Technology",
                            "ceo": "Jensen Huang",
                            "website": "https://www.nvidia.com",
                            "fullTimeEmployees": 29600,
                            "marketCap": 4967727100000,
                            "country": "US",
                            "city": "Santa Clara",
                            "state": "CA",
                            "address": "2788 San Tomas Expressway",
                            "phone": "408-486-2000",
                            "exchange": "NASDAQ",
                            "currency": "USD",
                            "isEtf": false,
                            "isActivelyTrading": true
                          }
                        ]
                        """)
                    .build());
            })
            .build();
        FmpClient client = new FmpClient(webClient);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "baseUrl", "https://financialmodelingprep.com");

        CompanyProfileDto profile = client.getCompanyProfile(" nvda ");

        assertThat(requestedUri.get().getPath()).isEqualTo("/stable/profile");
        assertThat(requestedUri.get().getQuery()).contains("symbol=NVDA", "apikey=test-key");
        assertThat(profile.companyName()).isEqualTo("NVIDIA Corporation");
        assertThat(profile.marketCap()).isEqualTo(4_967_727_100_000L);
        assertThat(profile.exchange()).isEqualTo("NASDAQ");
        assertThat(profile.isActivelyTrading()).isTrue();
    }

    @Test
    void shouldSearchSymbolsViaStableEndpoint() {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
            .exchangeFunction(request -> {
                requestedUri.set(request.url());
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("""
                        [
                          {
                            "symbol": "AAPL",
                            "name": "Apple Inc.",
                            "currency": "USD",
                            "stockExchange": "NASDAQ Global Select",
                            "exchangeShortName": "NASDAQ",
                            "type": "stock"
                          }
                        ]
                        """)
                    .build());
            })
            .build();
        FmpClient client = new FmpClient(webClient);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "baseUrl", "https://financialmodelingprep.com");

        var results = client.searchSymbols("aapl");

        assertThat(requestedUri.get().getPath()).isEqualTo("/stable/search-symbol");
        assertThat(requestedUri.get().getQuery()).contains("query=aapl", "apikey=test-key", "limit=25");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).symbol()).isEqualTo("AAPL");
        assertThat(results.get(0).name()).isEqualTo("Apple Inc.");
        assertThat(results.get(0).exchangeShortName()).isEqualTo("NASDAQ");
    }
}
