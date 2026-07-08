package com.marketmono.config.security;

import com.marketmono.repository.auth.UserRepository;
import com.marketmono.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigSupportTicketAccessTest.SecurityProbeController.class)
@Import({SecurityConfig.class, SecurityConfigSupportTicketAccessTest.TestBeans.class})
class SecurityConfigSupportTicketAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldPermitKnownPublicRouteThroughSameSecurityChain() throws Exception {
        mockMvc.perform(get("/api/v1/changelog/security-probe"))
            .andExpect(status().isOk())
            .andExpect(content().string("public"));
    }

    @Test
    void shouldPermitWhopWebhookRouteThroughSameSecurityChain() throws Exception {
        mockMvc.perform(post("/api/v1/whop/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(content().string("whop-webhook"));
    }

    @ParameterizedTest
    @MethodSource("publicNonUwRoutes")
    void shouldPermitKnownPublicNonUwRoutesThroughSameSecurityChain(String route) throws Exception {
        mockMvc.perform(get(route))
            .andExpect(status().isOk())
            .andExpect(content().string("public"));
    }

    @Test
    void shouldRejectAnonymousSupportTicketAccessUsingRealSecurityChain() throws Exception {
        mockMvc.perform(get("/api/v1/support/tickets"))
            .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @MethodSource("anonymousUwProxyRoutes")
    void shouldRejectAnonymousUwProxyRoutesUsingRealSecurityChain(String route) throws Exception {
        mockMvc.perform(get(route))
            .andExpect(status().isUnauthorized());
    }

    static Stream<String> anonymousUwProxyRoutes() {
        return Stream.of(
            "/api/v1/congress-recent-trades",
            "/api/v1/greek-exposure-by-strike/AAPL",
            "/api/v1/greek-exposure-strike-expiry/AAPL",
            "/api/v1/ticker-options-volume",
            "/api/v1/price-change-per-month/AAPL",
            "/api/v1/analyst-ratings/AAPL",
            "/api/v1/insider-trades",
            "/api/v1/option-chains/AAPL",
            "/api/v1/darkpool/AAPL",
            "/api/v1/spot-exposure-by-strike-expiry/AAPL",
            "/api/v1/earnings-reports",
            "/api/v1/stock-information/AAPL",
            "/api/v1/seasonality/AAPL/year-month",
            "/api/v1/options-price-levels/AAPL",
            "/api/v1/option-contract-historic/AAPL230616C00150000",
            "/api/v1/option-contract-intraday/AAPL230616C00150000",
            "/api/v1/option-contracts-all/AAPL",
            "/api/v1/iv-rank/AAPL",
            "/api/v1/realized-volatility/AAPL",
            "/api/v1/GEX-Strike-Expiry-Range/AAPL",
            "/api/v1/institution/AAPL/ownership",
            "/api/v1/etfs/SPY/holdings",
            "/api/v1/option-trades/screener/option-contracts",
            "/api/v1/stock/AAPL/stock-state",
            "/api/v1/stock/AAPL/max-pain",
            "/api/stock/AAPL/stock-state",
            "/api/darkpool/AAPL"
        );
    }

    static Stream<String> publicNonUwRoutes() {
        return Stream.of(
            "/api/v1/ticker-bio-info",
            "/api/v1/congress-member-details",
            "/api/v1/econ_events"
        );
    }

    @RestController
    public static class SecurityProbeController {

        @GetMapping("/api/v1/changelog/security-probe")
        ResponseEntity<String> publicProbe() {
            return ResponseEntity.ok("public");
        }

        @GetMapping({
            "/api/v1/ticker-bio-info",
            "/api/v1/congress-member-details",
            "/api/v1/econ_events"
        })
        ResponseEntity<String> publicNonUwProbe() {
            return ResponseEntity.ok("public");
        }

        @GetMapping("/api/v1/support/tickets")
        ResponseEntity<String> supportProbe() {
            return ResponseEntity.ok("support");
        }

        @PostMapping("/api/v1/whop/webhooks")
        ResponseEntity<String> whopWebhookProbe() {
            return ResponseEntity.ok("whop-webhook");
        }

        @GetMapping({
            "/api/v1/congress-recent-trades",
            "/api/v1/greek-exposure-by-strike/{ticker}",
            "/api/v1/greek-exposure-strike-expiry/{ticker}",
            "/api/v1/ticker-options-volume",
            "/api/v1/price-change-per-month/{ticker}",
            "/api/v1/analyst-ratings/{ticker}",
            "/api/v1/insider-trades",
            "/api/v1/option-chains/{ticker}",
            "/api/v1/darkpool/{ticker}",
            "/api/v1/spot-exposure-by-strike-expiry/{ticker}",
            "/api/v1/earnings-reports",
            "/api/v1/stock-information/{ticker}",
            "/api/v1/seasonality/{ticker}/year-month",
            "/api/v1/options-price-levels/{ticker}",
            "/api/v1/option-contract-historic/{id}",
            "/api/v1/option-contract-intraday/{id}",
            "/api/v1/option-contracts-all/{ticker}",
            "/api/v1/iv-rank/{ticker}",
            "/api/v1/realized-volatility/{ticker}",
            "/api/v1/GEX-Strike-Expiry-Range/{ticker}",
            "/api/v1/institution/{ticker}/ownership",
            "/api/v1/etfs/{ticker}/holdings",
            "/api/v1/option-trades/screener/option-contracts",
            "/api/v1/stock/{ticker}/stock-state",
            "/api/v1/stock/{ticker}/max-pain",
            "/api/stock/{ticker}/stock-state",
            "/api/darkpool/{ticker}"
        })
        ResponseEntity<String> uwProxyProbe() {
            return ResponseEntity.ok("uw-proxy");
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        SecurityProbeController securityProbeController() {
            return new SecurityProbeController();
        }

        @Bean
        InternalAdminApiAuthenticationFilter internalAdminApiAuthenticationFilter() {
            return new InternalAdminApiAuthenticationFilter(new InternalAdminApiProperties(null));
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter(mock(JwtTokenProvider.class), mock(UserRepository.class));
        }

        @Bean
        PasswordChangeRequiredFilter passwordChangeRequiredFilter() {
            return new PasswordChangeRequiredFilter();
        }

        @Bean
        SubscriptionAccessFilter subscriptionAccessFilter() {
            return new SubscriptionAccessFilter(mock(SubscriptionService.class), mock(UserRepository.class));
        }

        @Bean
        CustomUserDetailsService customUserDetailsService() {
            return mock(CustomUserDetailsService.class);
        }
    }
}
