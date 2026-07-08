package com.marketmono.repository;

import com.marketmono.entity.UserPreference;
import com.marketmono.entity.UserPreferenceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

    List<UserPreference> findByUser_IdOrderByCategoryAsc(UUID userId);

    Optional<UserPreference> findByUser_IdAndCategory(UUID userId, UserPreferenceCategory category);

    List<UserPreference> findByCategory(UserPreferenceCategory category);
}
