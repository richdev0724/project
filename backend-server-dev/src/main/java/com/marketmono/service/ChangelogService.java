package com.marketmono.service;

import com.marketmono.dto.AdminChangelogEntryDto;
import com.marketmono.dto.CreateChangelogEntryRequest;
import com.marketmono.dto.PublicChangelogChangeDto;
import com.marketmono.dto.PublicChangelogReleaseDto;
import com.marketmono.dto.UpdateChangelogEntryRequest;
import com.marketmono.entity.ChangelogEntry;
import com.marketmono.entity.ChangelogEntryType;
import com.marketmono.entity.ChangelogEntryVisibility;
import com.marketmono.repository.ChangelogEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Service for public changelog reads and admin changelog CRUD operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChangelogService {

    private static final Comparator<ChangelogEntry> ENTRY_SORT = Comparator
        .comparing(ChangelogEntry::getReleaseDate, Comparator.reverseOrder())
        .thenComparing(ChangelogEntry::getReleaseVersion, Comparator.reverseOrder())
        .thenComparingInt(ChangelogEntry::getSortOrder)
        .thenComparing(ChangelogEntry::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(ChangelogEntry::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final ChangelogEntryRepository changelogEntryRepository;

    @Transactional(readOnly = true)
    public List<PublicChangelogReleaseDto> getPublicChangelog() {
        List<ChangelogEntry> entries = changelogEntryRepository.findAllByVisibility(ChangelogEntryVisibility.VISIBLE);

        LinkedHashMap<ReleaseKey, List<PublicChangelogChangeDto>> grouped = new LinkedHashMap<>();
        entries.stream()
            .sorted(ENTRY_SORT)
            .forEach(entry -> grouped
                .computeIfAbsent(new ReleaseKey(entry.getReleaseVersion(), entry.getReleaseDate()), ignored -> new ArrayList<>())
                .add(PublicChangelogChangeDto.fromEntity(entry)));

        return grouped.entrySet().stream()
            .map(entry -> new PublicChangelogReleaseDto(
                entry.getKey().version(),
                entry.getKey().date(),
                List.copyOf(entry.getValue())
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminChangelogEntryDto> getAdminEntries(String visibilityFilter) {
        return loadEntriesForFilter(visibilityFilter).stream()
            .sorted(ENTRY_SORT)
            .map(AdminChangelogEntryDto::fromEntity)
            .toList();
    }

    @Transactional
    public AdminChangelogEntryDto createEntry(CreateChangelogEntryRequest request, UUID adminId) {
        String actorId = requireAdminId(adminId);

        ChangelogEntry entry = ChangelogEntry.builder()
            .createdBy(actorId)
            .updatedBy(actorId)
            .build();
        populateEntry(
            entry,
            request.releaseVersion(),
            request.releaseDate(),
            request.entryType(),
            request.entryText(),
            request.sortOrder(),
            request.visibility()
        );

        ChangelogEntry saved = changelogEntryRepository.save(entry);
        log.info("Created changelog entry {} for release {} by admin {}",
            saved.getId(), saved.getReleaseVersion(), actorId);

        return AdminChangelogEntryDto.fromEntity(saved);
    }

    @Transactional
    public AdminChangelogEntryDto updateEntry(UUID entryId, UpdateChangelogEntryRequest request, UUID adminId) {
        String actorId = requireAdminId(adminId);
        ChangelogEntry entry = findEntry(entryId);

        populateEntry(
            entry,
            request.releaseVersion(),
            request.releaseDate(),
            request.entryType(),
            request.entryText(),
            request.sortOrder(),
            request.visibility()
        );
        entry.setUpdatedBy(actorId);

        ChangelogEntry saved = changelogEntryRepository.save(entry);
        log.info("Updated changelog entry {} for release {} by admin {}",
            saved.getId(), saved.getReleaseVersion(), actorId);

        return AdminChangelogEntryDto.fromEntity(saved);
    }

    @Transactional
    public void deleteEntry(UUID entryId) {
        ChangelogEntry entry = findEntry(entryId);
        changelogEntryRepository.delete(entry);
        log.info("Deleted changelog entry {} for release {}", entry.getId(), entry.getReleaseVersion());
    }

    private List<ChangelogEntry> loadEntriesForFilter(String visibilityFilter) {
        if (visibilityFilter == null || visibilityFilter.isBlank() || "ALL".equalsIgnoreCase(visibilityFilter)) {
            return changelogEntryRepository.findAll();
        }

        return changelogEntryRepository.findAllByVisibility(parseVisibility(visibilityFilter));
    }

    private ChangelogEntry findEntry(UUID entryId) {
        return changelogEntryRepository.findById(entryId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Changelog entry not found: " + entryId));
    }

    private void populateEntry(
            ChangelogEntry entry,
            String releaseVersion,
            LocalDate releaseDate,
            String entryType,
            String entryText,
            Integer sortOrder,
            String visibility) {
        entry.setReleaseVersion(normalizeRequiredText(releaseVersion, "Release version"));
        entry.setReleaseDate(requireReleaseDate(releaseDate));
        entry.setEntryType(parseEntryType(entryType));
        entry.setEntryText(normalizeRequiredText(entryText, "Entry text"));
        entry.setSortOrder(requireSortOrder(sortOrder));
        entry.setVisibility(parseVisibility(visibility));
    }

    private String requireAdminId(UUID adminId) {
        if (adminId == null) {
            throw new IllegalStateException("Internal admin requests must include the admin actor id header");
        }
        return adminId.toString();
    }

    private String normalizeRequiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw badRequest(fieldName + " is required");
        }
        return value.trim();
    }

    private LocalDate requireReleaseDate(LocalDate releaseDate) {
        if (releaseDate == null) {
            throw badRequest("Release date is required");
        }
        return releaseDate;
    }

    private int requireSortOrder(Integer sortOrder) {
        if (sortOrder == null) {
            throw badRequest("Sort order is required");
        }
        return sortOrder;
    }

    private ChangelogEntryType parseEntryType(String rawEntryType) {
        try {
            return ChangelogEntryType.valueOf(normalizeEnumValue(rawEntryType));
        } catch (IllegalArgumentException ex) {
            throw badRequest("Unsupported changelog entry type: " + rawEntryType);
        }
    }

    private ChangelogEntryVisibility parseVisibility(String rawVisibility) {
        try {
            return ChangelogEntryVisibility.valueOf(normalizeEnumValue(rawVisibility));
        } catch (IllegalArgumentException ex) {
            throw badRequest("Unsupported changelog visibility: " + rawVisibility);
        }
    }

    private String normalizeEnumValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw badRequest("Value is required");
        }

        return rawValue.trim()
            .replace('-', '_')
            .toUpperCase(Locale.ROOT);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(BAD_REQUEST, message);
    }

    private record ReleaseKey(String version, LocalDate date) {}
}