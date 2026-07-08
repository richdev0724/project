package com.marketmono.service;

import com.marketmono.dto.AdminChangelogEntryDto;
import com.marketmono.dto.CreateChangelogEntryRequest;
import com.marketmono.dto.PublicChangelogReleaseDto;
import com.marketmono.dto.UpdateChangelogEntryRequest;
import com.marketmono.entity.ChangelogEntry;
import com.marketmono.entity.ChangelogEntryType;
import com.marketmono.entity.ChangelogEntryVisibility;
import com.marketmono.repository.ChangelogEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangelogServiceTest {

    @Mock
    private ChangelogEntryRepository changelogEntryRepository;

    @InjectMocks
    private ChangelogService changelogService;

    @Test
    void shouldGroupVisibleEntriesForPublicChangelog() {
        ChangelogEntry latestFixed = entry(
            UUID.randomUUID(),
            "2.8.0",
            LocalDate.of(2025, 2, 28),
            ChangelogEntryType.FIXED,
            "Fixed critical issue",
            20,
            ChangelogEntryVisibility.VISIBLE,
            LocalDateTime.of(2025, 2, 28, 10, 0)
        );
        ChangelogEntry olderNew = entry(
            UUID.randomUUID(),
            "2.7.0",
            LocalDate.of(2025, 1, 30),
            ChangelogEntryType.NEW,
            "Added new dashboard module",
            10,
            ChangelogEntryVisibility.VISIBLE,
            LocalDateTime.of(2025, 1, 30, 9, 0)
        );
        ChangelogEntry latestNew = entry(
            UUID.randomUUID(),
            "2.8.0",
            LocalDate.of(2025, 2, 28),
            ChangelogEntryType.NEW,
            "Added changelog admin page",
            10,
            ChangelogEntryVisibility.VISIBLE,
            LocalDateTime.of(2025, 2, 28, 8, 0)
        );

        when(changelogEntryRepository.findAllByVisibility(ChangelogEntryVisibility.VISIBLE))
            .thenReturn(List.of(latestFixed, olderNew, latestNew));

        List<PublicChangelogReleaseDto> releases = changelogService.getPublicChangelog();

        assertThat(releases).hasSize(2);
        assertThat(releases.getFirst().version()).isEqualTo("2.8.0");
        assertThat(releases.getFirst().changes())
            .extracting(change -> change.type() + ":" + change.text())
            .containsExactly(
                "new:Added changelog admin page",
                "fixed:Fixed critical issue"
            );
        assertThat(releases.get(1).version()).isEqualTo("2.7.0");
        assertThat(releases.get(1).changes())
            .extracting(change -> change.type())
            .containsExactly("new");
    }

    @Test
    void shouldReturnAllAdminEntriesForAllVisibilityFilter() {
        ChangelogEntry archived = entry(
            UUID.randomUUID(),
            "2.0.0",
            LocalDate.of(2024, 1, 1),
            ChangelogEntryType.NEW,
            "Archived item",
            10,
            ChangelogEntryVisibility.ARCHIVED,
            LocalDateTime.of(2024, 1, 1, 10, 0)
        );
        ChangelogEntry visible = entry(
            UUID.randomUUID(),
            "2.8.0",
            LocalDate.of(2025, 2, 28),
            ChangelogEntryType.IMPROVED,
            "Visible item",
            10,
            ChangelogEntryVisibility.VISIBLE,
            LocalDateTime.of(2025, 2, 28, 10, 0)
        );

        when(changelogEntryRepository.findAll()).thenReturn(List.of(archived, visible));

        List<AdminChangelogEntryDto> entries = changelogService.getAdminEntries("ALL");

        assertThat(entries).hasSize(2);
        assertThat(entries.getFirst().releaseVersion()).isEqualTo("2.8.0");
        assertThat(entries.getFirst().entryType()).isEqualTo("improved");
        assertThat(entries.getFirst().visibility()).isEqualTo("visible");
        assertThat(entries.get(1).visibility()).isEqualTo("archived");
    }

    @Test
    void shouldCreateEntryWithNormalizedValuesAndActor() {
        UUID adminId = UUID.randomUUID();
        CreateChangelogEntryRequest request = new CreateChangelogEntryRequest(
            " 2.9.0 ",
            LocalDate.of(2025, 3, 1),
            "new",
            " Added live changelog management ",
            10,
            "visible"
        );
        UUID createdId = UUID.randomUUID();

        when(changelogEntryRepository.save(any(ChangelogEntry.class))).thenAnswer(invocation -> {
            ChangelogEntry entry = invocation.getArgument(0);
            entry.setId(createdId);
            entry.setCreatedAt(LocalDateTime.of(2025, 3, 1, 12, 0));
            entry.setUpdatedAt(LocalDateTime.of(2025, 3, 1, 12, 0));
            return entry;
        });

        AdminChangelogEntryDto created = changelogService.createEntry(request, adminId);

        ArgumentCaptor<ChangelogEntry> captor = ArgumentCaptor.forClass(ChangelogEntry.class);
        verify(changelogEntryRepository).save(captor.capture());
        ChangelogEntry saved = captor.getValue();

        assertThat(saved.getReleaseVersion()).isEqualTo("2.9.0");
        assertThat(saved.getEntryText()).isEqualTo("Added live changelog management");
        assertThat(saved.getEntryType()).isEqualTo(ChangelogEntryType.NEW);
        assertThat(saved.getVisibility()).isEqualTo(ChangelogEntryVisibility.VISIBLE);
        assertThat(saved.getCreatedBy()).isEqualTo(adminId.toString());
        assertThat(saved.getUpdatedBy()).isEqualTo(adminId.toString());

        assertThat(created.id()).isEqualTo(createdId);
        assertThat(created.entryType()).isEqualTo("new");
        assertThat(created.visibility()).isEqualTo("visible");
    }

    @Test
    void shouldUpdateExistingEntry() {
        UUID entryId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        ChangelogEntry existing = entry(
            entryId,
            "2.8.0",
            LocalDate.of(2025, 2, 28),
            ChangelogEntryType.NEW,
            "Old text",
            10,
            ChangelogEntryVisibility.VISIBLE,
            LocalDateTime.of(2025, 2, 28, 8, 0)
        );

        when(changelogEntryRepository.findById(entryId)).thenReturn(java.util.Optional.of(existing));
        when(changelogEntryRepository.save(existing)).thenAnswer(invocation -> invocation.getArgument(0));

        AdminChangelogEntryDto updated = changelogService.updateEntry(
            entryId,
            new UpdateChangelogEntryRequest(
                "2.8.1",
                LocalDate.of(2025, 3, 2),
                "fixed",
                "Updated text",
                30,
                "hidden"
            ),
            adminId
        );

        assertThat(existing.getReleaseVersion()).isEqualTo("2.8.1");
        assertThat(existing.getReleaseDate()).isEqualTo(LocalDate.of(2025, 3, 2));
        assertThat(existing.getEntryType()).isEqualTo(ChangelogEntryType.FIXED);
        assertThat(existing.getEntryText()).isEqualTo("Updated text");
        assertThat(existing.getSortOrder()).isEqualTo(30);
        assertThat(existing.getVisibility()).isEqualTo(ChangelogEntryVisibility.HIDDEN);
        assertThat(existing.getUpdatedBy()).isEqualTo(adminId.toString());
        assertThat(updated.entryType()).isEqualTo("fixed");
        assertThat(updated.visibility()).isEqualTo("hidden");
    }

    @Test
    void shouldRejectUnsupportedEntryType() {
        CreateChangelogEntryRequest request = new CreateChangelogEntryRequest(
            "2.9.0",
            LocalDate.of(2025, 3, 1),
            "unknown",
            "Bad type",
            10,
            "visible"
        );

        assertThatThrownBy(() -> changelogService.createEntry(request, UUID.randomUUID()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Unsupported changelog entry type");
    }

    private ChangelogEntry entry(
            UUID id,
            String releaseVersion,
            LocalDate releaseDate,
            ChangelogEntryType entryType,
            String entryText,
            int sortOrder,
            ChangelogEntryVisibility visibility,
            LocalDateTime createdAt) {
        return ChangelogEntry.builder()
            .id(id)
            .releaseVersion(releaseVersion)
            .releaseDate(releaseDate)
            .entryType(entryType)
            .entryText(entryText)
            .sortOrder(sortOrder)
            .visibility(visibility)
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .createdBy("seed")
            .updatedBy("seed")
            .build();
    }
}