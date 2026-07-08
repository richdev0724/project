package com.marketmono.controller;

import com.marketmono.config.security.InternalAdminPrincipal;
import com.marketmono.config.security.UserPrincipal;
import com.marketmono.dto.CollectionConfigDto;
import com.marketmono.dto.SpotGexCollectionStatusDto;
import com.marketmono.dto.UpdateTickersRequest;
import com.marketmono.entity.auth.User;
import com.marketmono.service.CollectionConfigService;
import com.marketmono.service.SpotGexQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
class CollectionConfigControllerTest {

    @Mock
    private CollectionConfigService collectionConfigService;

    @Mock
    private SpotGexQueryService spotGexQueryService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private CollectionConfigController collectionConfigController;

    @Test
    void shouldReturnAdminCollectionStatus() {
        SpotGexCollectionStatusDto status = new SpotGexCollectionStatusDto(
            "SPY",
            Instant.parse("2026-04-06T15:31:00Z"),
            144,
            12,
            Instant.parse("2026-04-06T15:31:00Z"),
            Instant.parse("2026-04-06T15:30:00Z"),
            null,
            0,
            true,
            true
        );
        when(spotGexQueryService.getCollectionStatus()).thenReturn(List.of(status));

        ResponseEntity<List<SpotGexCollectionStatusDto>> response =
            collectionConfigController.getSpotGexCollectionStatus();

        assertThat(response.getBody()).containsExactly(status);
    }

    @Test
    void shouldAcceptInternalAdminPrincipalForTickerUpdate() {
        UUID adminId = UUID.randomUUID();
        UpdateTickersRequest request = new UpdateTickersRequest(List.of("SPY"));
        CollectionConfigDto response = collectionConfigDto();
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10");
        when(collectionConfigService.updateSpotGexTickers(request.tickers(), adminId, "203.0.113.10"))
            .thenReturn(response);

        collectionConfigController.updateSpotGexTickers(new InternalAdminPrincipal(adminId), request, httpServletRequest);

        verify(collectionConfigService).updateSpotGexTickers(request.tickers(), adminId, "203.0.113.10");
    }

    @Test
    void shouldAcceptDirectUserForTickerUpdate() {
        UUID userId = UUID.randomUUID();
        UserDetails user = userDetailsBackedByUser(userId);
        UpdateTickersRequest request = new UpdateTickersRequest(List.of("SPY"));
        CollectionConfigDto response = collectionConfigDto();
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10");
        when(collectionConfigService.updateSpotGexTickers(request.tickers(), userId, "203.0.113.10"))
            .thenReturn(response);

        collectionConfigController.updateSpotGexTickers(user, request, httpServletRequest);

        verify(collectionConfigService).updateSpotGexTickers(request.tickers(), userId, "203.0.113.10");
    }

    @Test
    void shouldRejectUserPrincipalForTickerUpdate() {
        assertThatThrownBy(() -> collectionConfigController.updateSpotGexTickers(
            new UserPrincipal(user(UUID.randomUUID())),
            new UpdateTickersRequest(List.of("SPY")),
            httpServletRequest
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unable to extract user ID");
    }

    private static User user(UUID userId) {
        return User.builder()
            .id(userId)
            .email("admin@example.com")
            .enabled(true)
            .emailVerified(true)
            .build();
    }

    private static UserDetails userDetailsBackedByUser(UUID userId) {
        User user = mock(User.class, withSettings().extraInterfaces(UserDetails.class));
        when(user.getId()).thenReturn(userId);
        return (UserDetails) user;
    }

    private static CollectionConfigDto collectionConfigDto() {
        return new CollectionConfigDto(
            "spot_gex_tickers",
            List.of("SPY"),
            "Tracked tickers",
            "admin",
            LocalDateTime.now()
        );
    }
}
