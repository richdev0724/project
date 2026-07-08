package com.marketmono.service.spotgex;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SpotGexOneMinuteBucketAggregator {

    private static final long ONE_MINUTE_MS = 60_000L;

    private final Map<String, SpotGexStreamEvent> latestEventsByBucketKey = new ConcurrentHashMap<>();

    public void accept(SpotGexStreamEvent event) {
        if (event == null) {
            return;
        }

        latestEventsByBucketKey.merge(
            event.bucketKey(),
            event,
            (current, incoming) -> incoming.timestampMs() >= current.timestampMs() ? incoming : current
        );
    }

    public List<SpotGexStreamBucket> drainReadyBuckets(long watermarkMs) {
        List<SpotGexStreamEvent> readyEvents = latestEventsByBucketKey.values().stream()
            .filter(event -> event.bucketStartMs() + ONE_MINUTE_MS <= watermarkMs)
            .sorted(Comparator
                .comparingLong(SpotGexStreamEvent::bucketStartMs)
                .thenComparing(SpotGexStreamEvent::ticker)
                .thenComparing(SpotGexStreamEvent::expiry)
                .thenComparing(SpotGexStreamEvent::strikePrice))
            .toList();

        readyEvents.forEach(event -> latestEventsByBucketKey.remove(event.bucketKey(), event));

        return readyEvents.stream()
            .map(SpotGexStreamBucket::fromLatestEvent)
            .toList();
    }
}
