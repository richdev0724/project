package com.marketmono.repository;

import com.marketmono.entity.BookmarkSourceType;
import com.marketmono.entity.OptionTradeBookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OptionTradeBookmarkRepository extends JpaRepository<OptionTradeBookmark, Long> {

    List<OptionTradeBookmark> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<OptionTradeBookmark> findByUserIdAndTradeId(UUID userId, String tradeId);

    boolean existsByUserIdAndTradeId(UUID userId, String tradeId);

    boolean existsByUserIdAndTradeIdAndSourceType(UUID userId, String tradeId, BookmarkSourceType sourceType);

    void deleteByUserIdAndTradeId(UUID userId, String tradeId);

    void deleteByUserIdAndTradeIdIn(UUID userId, List<String> tradeIds);

    long countByUserId(UUID userId);
}
