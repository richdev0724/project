package com.marketmono.repository;

import com.marketmono.entity.ChangelogEntry;
import com.marketmono.entity.ChangelogEntryVisibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for flat changelog entry records.
 */
public interface ChangelogEntryRepository extends JpaRepository<ChangelogEntry, UUID> {

    List<ChangelogEntry> findAllByVisibility(ChangelogEntryVisibility visibility);
}