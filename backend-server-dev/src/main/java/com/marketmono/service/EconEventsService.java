package com.marketmono.service;

import com.marketmono.dto.EconEventDto;
import com.marketmono.entity.EconomicEvent;
import com.marketmono.repository.EconomicEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EconEventsService {

    private final EconomicEventRepository economicEventRepository;

    public Map<String, List<EconEventDto>> getEconEvents(String startDate, String endDate) {
        String normalizedStart = normalizeDate(startDate);
        String normalizedEnd = normalizeDate(endDate);
        LocalDate start = parseDate(normalizedStart);
        LocalDate end = parseDate(normalizedEnd);
        List<EconomicEvent> events = economicEventRepository.findActiveByOptionalCacheDateRange(start, end);

        return events.stream()
            .collect(Collectors.groupingBy(
                event -> event.getCacheDate().toString(),
                LinkedHashMap::new,
                Collectors.mapping(this::toDto, Collectors.toList())
            ));
    }

    private EconEventDto toDto(EconomicEvent event) {
        return new EconEventDto(
                LocalTime.ofInstant(event.getEventTime(), ZoneOffset.UTC).toString(),
                event.getEvent(),
                event.getPriority().name().toLowerCase(),
                event.getForecast(),
                event.getPrev(),
                event.getType().getValue(),
                event.getReportedPeriod(),
                event.getEventTime().toString()
        );
    }

    private String normalizeDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private LocalDate parseDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid economic event date: " + value);
        }
    }
}
