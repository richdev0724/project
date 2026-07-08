package com.marketmono.repository;

import com.marketmono.entity.StoredNewsHeadline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface StoredNewsHeadlineRepository extends JpaRepository<StoredNewsHeadline, Long> {

    boolean existsByDedupeKey(String dedupeKey);

    List<StoredNewsHeadline> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
        OffsetDateTime startInclusive,
        OffsetDateTime endExclusive
    );
}
