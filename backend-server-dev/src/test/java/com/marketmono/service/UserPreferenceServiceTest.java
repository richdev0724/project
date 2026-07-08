package com.marketmono.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmono.dto.UserPreferenceDTO;
import com.marketmono.entity.UserPreference;
import com.marketmono.entity.UserPreferenceCategory;
import com.marketmono.entity.auth.User;
import com.marketmono.repository.UserPreferenceRepository;
import com.marketmono.repository.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPreferenceServiceTest {

    @Mock
    private UserPreferenceRepository userPreferenceRepository;

    @Mock
    private UserRepository userRepository;

    private UserPreferenceService userPreferenceService;

    @BeforeEach
    void setUp() {
        userPreferenceService = new UserPreferenceService(
            userPreferenceRepository,
            userRepository,
            new ObjectMapper()
        );
    }

    @Test
    void shouldReturnParsedPreferencePayloads() {
        UUID userId = UUID.randomUUID();
        UserPreference preference = new UserPreference();
        preference.setId(1L);
        preference.setCategory(UserPreferenceCategory.NOTIFICATIONS);
        preference.setPreferencesJson("{\"marketing_emails\":true}");
        preference.setUpdatedAt(Instant.now());

        when(userPreferenceRepository.findByUser_IdOrderByCategoryAsc(userId))
            .thenReturn(List.of(preference));

        List<UserPreferenceDTO> result = userPreferenceService.getPreferences(userId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().category()).isEqualTo(UserPreferenceCategory.NOTIFICATIONS);
        assertThat(result.getFirst().preferences().get("marketing_emails").asBoolean()).isTrue();
    }

    @Test
    void shouldUpsertPreferenceForUser() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        when(userPreferenceRepository.findByUser_IdAndCategory(userId, UserPreferenceCategory.FLOW))
            .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(userPreferenceRepository.save(any(UserPreference.class))).thenAnswer(invocation -> {
            UserPreference saved = invocation.getArgument(0);
            saved.setId(5L);
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        UserPreferenceDTO result = userPreferenceService.upsertPreference(
            userId,
            UserPreferenceCategory.FLOW,
            new ObjectMapper().readTree("{\"activeFilterId\":42}")
        );

        ArgumentCaptor<UserPreference> captor = ArgumentCaptor.forClass(UserPreference.class);
        verify(userPreferenceRepository).save(captor.capture());

        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().getCategory()).isEqualTo(UserPreferenceCategory.FLOW);
        assertThat(captor.getValue().getPreferencesJson()).contains("activeFilterId");
        assertThat(result.preferences().get("activeFilterId").asInt()).isEqualTo(42);
    }

    @Test
    void shouldUpsertFlowTestingPreferenceForUser() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        when(userPreferenceRepository.findByUser_IdAndCategory(userId, UserPreferenceCategory.FLOW_TESTING))
            .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(userPreferenceRepository.save(any(UserPreference.class))).thenAnswer(invocation -> {
            UserPreference saved = invocation.getArgument(0);
            saved.setId(7L);
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        UserPreferenceDTO result = userPreferenceService.upsertPreference(
            userId,
            UserPreferenceCategory.FLOW_TESTING,
            new ObjectMapper().readTree("{\"draftFilter\":{\"mid\":false,\"expiration\":[\"2026-07-17\"]}}")
        );

        ArgumentCaptor<UserPreference> captor = ArgumentCaptor.forClass(UserPreference.class);
        verify(userPreferenceRepository).save(captor.capture());

        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().getCategory()).isEqualTo(UserPreferenceCategory.FLOW_TESTING);
        assertThat(captor.getValue().getPreferencesJson()).contains("draftFilter");
        assertThat(result.preferences().get("draftFilter").get("mid").asBoolean()).isFalse();
    }
}
