package com.marketmono.controller;

import com.marketmono.config.security.InternalAdminPrincipal;
import com.marketmono.config.security.UserPrincipal;
import com.marketmono.dto.AdminChangelogEntryDto;
import com.marketmono.dto.CreateChangelogEntryRequest;
import com.marketmono.dto.UpdateChangelogEntryRequest;
import com.marketmono.entity.auth.User;
import com.marketmono.service.ChangelogService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
class AdminChangelogControllerTest {

    @Mock
    private ChangelogService changelogService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private AdminChangelogController adminChangelogController;

    @Test
    void shouldReturnAdminEntries() {
        AdminChangelogEntryDto entry = entryDto();
        when(changelogService.getAdminEntries("ALL")).thenReturn(List.of(entry));

        ResponseEntity<List<AdminChangelogEntryDto>> response = adminChangelogController.getEntries("ALL");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(entry);
        verify(changelogService).getAdminEntries("ALL");
    }

    @Test
    void shouldCreateEntry() {
        UUID adminId = UUID.randomUUID();
        InternalAdminPrincipal principal = new InternalAdminPrincipal(adminId);
        CreateChangelogEntryRequest request = new CreateChangelogEntryRequest(
            "2.8.0",
            LocalDate.of(2025, 2, 28),
            "new",
            "Created from admin",
            10,
            "visible"
        );
        AdminChangelogEntryDto responseDto = entryDto();

        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10, 10.0.0.5");
        when(changelogService.createEntry(request, adminId)).thenReturn(responseDto);

        ResponseEntity<AdminChangelogEntryDto> response =
            adminChangelogController.createEntry(principal, request, httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(responseDto);
        verify(changelogService).createEntry(request, adminId);
    }

    @Test
    void shouldAcceptDirectUserForCreateEntry() {
        UUID userId = UUID.randomUUID();
        UserDetails user = userDetailsBackedByUser(userId);
        CreateChangelogEntryRequest request = createRequest();
        AdminChangelogEntryDto responseDto = entryDto();

        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10");
        when(changelogService.createEntry(request, userId)).thenReturn(responseDto);

        ResponseEntity<AdminChangelogEntryDto> response =
            adminChangelogController.createEntry(user, request, httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(responseDto);
        verify(changelogService).createEntry(request, userId);
    }

    @Test
    void shouldRejectUserPrincipalForCreateEntry() {
        assertThatThrownBy(() -> adminChangelogController.createEntry(
            new UserPrincipal(user(UUID.randomUUID())),
            createRequest(),
            httpServletRequest
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unable to extract user ID");
    }

    @Test
    void shouldUpdateEntry() {
        UUID adminId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        InternalAdminPrincipal principal = new InternalAdminPrincipal(adminId);
        UpdateChangelogEntryRequest request = new UpdateChangelogEntryRequest(
            "2.8.1",
            LocalDate.of(2025, 3, 1),
            "fixed",
            "Updated from admin",
            20,
            "hidden"
        );
        AdminChangelogEntryDto responseDto = entryDto();

        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10");
        when(changelogService.updateEntry(entryId, request, adminId)).thenReturn(responseDto);

        ResponseEntity<AdminChangelogEntryDto> response =
            adminChangelogController.updateEntry(entryId, principal, request, httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(responseDto);
        verify(changelogService).updateEntry(entryId, request, adminId);
    }

    @Test
    void shouldDeleteEntry() {
        UUID adminId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        InternalAdminPrincipal principal = new InternalAdminPrincipal(adminId);

        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10");

        ResponseEntity<Void> response =
            adminChangelogController.deleteEntry(entryId, principal, httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(changelogService).deleteEntry(entryId);
    }

    private AdminChangelogEntryDto entryDto() {
        return new AdminChangelogEntryDto(
            UUID.randomUUID(),
            "2.8.0",
            LocalDate.of(2025, 2, 28),
            "new",
            "Created from admin",
            10,
            "visible",
            "admin",
            "admin",
            LocalDateTime.of(2025, 2, 28, 8, 0),
            LocalDateTime.of(2025, 2, 28, 8, 0)
        );
    }

    private CreateChangelogEntryRequest createRequest() {
        return new CreateChangelogEntryRequest(
            "2.8.0",
            LocalDate.of(2025, 2, 28),
            "new",
            "Created from admin",
            10,
            "visible"
        );
    }

    private User user(UUID userId) {
        return User.builder()
            .id(userId)
            .email("admin@example.com")
            .enabled(true)
            .emailVerified(true)
            .build();
    }

    private static UserDetails userDetailsBackedByUser(UUID userId) {
        User user = mock(User.class, withSettings().extraInterfaces(UserDetails.class));
        when(user.getId()).thenReturn(userId);
        return (UserDetails) user;
    }
}
