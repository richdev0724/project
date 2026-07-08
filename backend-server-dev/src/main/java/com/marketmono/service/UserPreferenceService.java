package com.marketmono.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmono.dto.UserPreferenceDTO;
import com.marketmono.entity.UserPreference;
import com.marketmono.entity.UserPreferenceCategory;
import com.marketmono.repository.UserPreferenceRepository;
import com.marketmono.repository.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserPreferenceService {

    private final UserPreferenceRepository userPreferenceRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<UserPreferenceDTO> getPreferences(UUID userId) {
        return userPreferenceRepository.findByUser_IdOrderByCategoryAsc(userId).stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public UserPreferenceDTO upsertPreference(UUID userId, UserPreferenceCategory category, JsonNode preferences) {
        UserPreference preference = userPreferenceRepository.findByUser_IdAndCategory(userId, category)
            .orElseGet(UserPreference::new);

        if (preference.getId() == null) {
            preference.setUser(userRepository.getReferenceById(userId));
            preference.setCategory(category);
        }

        preference.setPreferencesJson(serializePreferences(preferences));

        UserPreference saved = userPreferenceRepository.save(preference);
        log.info("Updated {} preferences for user {}", category, userId);
        return toDto(saved);
    }

    private UserPreferenceDTO toDto(UserPreference preference) {
        return new UserPreferenceDTO(
            preference.getCategory(),
            deserializePreferences(preference.getPreferencesJson()),
            preference.getUpdatedAt()
        );
    }

    private String serializePreferences(JsonNode preferences) {
        try {
            JsonNode normalizedPreferences = preferences == null || preferences.isNull()
                ? objectMapper.createObjectNode()
                : preferences;
            return objectMapper.writeValueAsString(normalizedPreferences);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize user preferences", exception);
        }
    }

    private JsonNode deserializePreferences(String preferencesJson) {
        try {
            return objectMapper.readTree(preferencesJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize user preferences", exception);
        }
    }
}
