package com.marketmono.service;

import com.marketmono.client.UnusualWhalesClient;
import com.marketmono.dto.OptionTradeDto;
import com.marketmono.entity.BookmarkSourceType;
import com.marketmono.entity.FlowAlert;
import com.marketmono.entity.OptionTradeBookmark;
import com.marketmono.entity.auth.User;
import com.marketmono.repository.FlowAlertRepository;
import com.marketmono.repository.OptionTradeBookmarkRepository;
import com.marketmono.repository.OptionTradeRepository;
import com.marketmono.repository.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OptionTradeBookmarkService {

    private static final int RECENT_FLOW_ALERT_LOOKUP_LIMIT = 500;

    private final OptionTradeBookmarkRepository bookmarkRepository;
    private final OptionTradeRepository optionTradeRepository;
    private final FlowAlertRepository flowAlertRepository;
    private final UserRepository userRepository;
    private final UnusualWhalesClient unusualWhalesClient;

    @Transactional(readOnly = true)
    public List<String> getBookmarkedTradeIds(UUID userId) {
        return bookmarkRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(OptionTradeBookmark::getTradeId)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<OptionTradeDto> getBookmarkedTrades(UUID userId) {
        List<OptionTradeBookmark> bookmarks = bookmarkRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (bookmarks.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> bookmarkOrder = new HashMap<>();
        for (int index = 0; index < bookmarks.size(); index++) {
            bookmarkOrder.putIfAbsent(bookmarks.get(index).getTradeId(), index);
        }

        // Group bookmark IDs by source type
        Map<BookmarkSourceType, List<UUID>> idsBySource = bookmarks.stream()
            .collect(Collectors.groupingBy(
                OptionTradeBookmark::getSourceType,
                Collectors.mapping(b -> UUID.fromString(b.getTradeId()), Collectors.toList())
            ));

        List<OptionTradeDto> results = new ArrayList<>();

        // Query option_trades for OT bookmarks
        List<UUID> otIds = idsBySource.getOrDefault(BookmarkSourceType.OT, List.of());
        if (!otIds.isEmpty()) {
            results.addAll(
                optionTradeRepository.findByTradeIdIn(otIds).stream()
                    .map(OptionTradeDto::from)
                    .toList()
            );
        }

        // Query flow_alerts for FA bookmarks
        List<UUID> faIds = idsBySource.getOrDefault(BookmarkSourceType.FA, List.of());
        if (!faIds.isEmpty()) {
            List<FlowAlert> resolvedFlowAlerts = flowAlertRepository.findByAlertIdIn(faIds);
            results.addAll(resolvedFlowAlerts.stream()
                .map(OptionTradeDto::from)
                .toList());

            Set<UUID> resolvedAlertIds = resolvedFlowAlerts.stream()
                .map(FlowAlert::getAlertId)
                .collect(Collectors.toSet());

            List<UUID> unresolvedFaIds = faIds.stream()
                .filter(alertId -> !resolvedAlertIds.contains(alertId))
                .toList();

            if (!unresolvedFaIds.isEmpty()) {
                results.addAll(resolveRecentFlowAlertBookmarks(unresolvedFaIds));
            }
        }

        results.sort(Comparator.comparingInt(dto ->
            bookmarkOrder.getOrDefault(dto.tradeId().toString(), Integer.MAX_VALUE)
        ));

        log.debug("Resolved bookmarks: {} from option_trades, {} from flow_alerts",
            otIds.size(), faIds.size());

        return results;
    }

    private List<OptionTradeDto> resolveRecentFlowAlertBookmarks(List<UUID> alertIds) {
        try {
            var response = unusualWhalesClient.getFlowAlerts(Map.of("limit", RECENT_FLOW_ALERT_LOOKUP_LIMIT));
            if (response == null || response.data() == null || response.data().isEmpty()) {
                return List.of();
            }

            Set<String> unresolvedIds = alertIds.stream()
                .map(UUID::toString)
                .collect(Collectors.toSet());

            List<OptionTradeDto> fallbackResults = response.data().stream()
                .filter(alert -> alert.id() != null && unresolvedIds.contains(alert.id()))
                .map(OptionTradeDto::from)
                .toList();

            log.debug("Resolved {} flow alert bookmarks from recent upstream feed", fallbackResults.size());
            return fallbackResults;
        } catch (Exception e) {
            log.warn("Failed to resolve flow alert bookmarks from upstream feed: {}", e.getMessage());
            return List.of();
        }
    }

    @Transactional
    public void addBookmarks(UUID userId, List<String> tradeIds, BookmarkSourceType sourceType) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        for (String tradeId : tradeIds) {
            if (!bookmarkRepository.existsByUserIdAndTradeIdAndSourceType(userId, tradeId, sourceType)) {
                OptionTradeBookmark bookmark = OptionTradeBookmark.builder()
                    .user(user)
                    .tradeId(tradeId)
                    .sourceType(sourceType)
                    .build();
                bookmarkRepository.save(bookmark);
            }
        }
        log.debug("Added {} bookmarks (source={}) for user {}", tradeIds.size(), sourceType, userId);
    }

    @Transactional
    public void removeBookmarks(UUID userId, List<String> tradeIds) {
        bookmarkRepository.deleteByUserIdAndTradeIdIn(userId, tradeIds);
        log.debug("Removed {} bookmarks for user {}", tradeIds.size(), userId);
    }
}
