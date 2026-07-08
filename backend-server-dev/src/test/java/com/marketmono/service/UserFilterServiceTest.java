package com.marketmono.service;

import com.marketmono.dto.CreateUserFilterRequest;
import com.marketmono.dto.UserFilterDTO;
import com.marketmono.entity.UserFilter;
import com.marketmono.entity.UserFilterType;
import com.marketmono.entity.auth.User;
import com.marketmono.repository.UserFilterRepository;
import com.marketmono.repository.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserFilterServiceTest {

    @Mock
    private UserFilterRepository userFilterRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserFilterService userFilterService;

    @Test
    void shouldReturnOnlyFiltersForRequestedUserAndType() {
        UUID userId = UUID.randomUUID();
        UserFilter filter = new UserFilter();
        filter.setId(11L);
        filter.setName("Large Premium Flow");
        filter.setFilterType(UserFilterType.FLOW);
        filter.setFilterQuery("{\"minimumPremium\":500000}");
        filter.setUpdatedAt(Instant.now());

        when(userFilterRepository.findByUser_IdAndFilterTypeOrderByUpdatedAtDesc(userId, UserFilterType.FLOW))
            .thenReturn(List.of(filter));

        List<UserFilterDTO> result = userFilterService.getAllFilters(userId, "FLOW");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Large Premium Flow");
        assertThat(result.getFirst().filterType()).isEqualTo(UserFilterType.FLOW);
    }

    @Test
    void shouldReturnAlertFiltersForRequestedUser() {
        UUID userId = UUID.randomUUID();
        UserFilter filter = new UserFilter();
        filter.setId(12L);
        filter.setName("Repeated Hits Alerts");
        filter.setFilterType(UserFilterType.ALERTS);
        filter.setFilterQuery("{\"ruleNames\":[\"RepeatedHits\"]}");
        filter.setUpdatedAt(Instant.now());

        when(userFilterRepository.findByUser_IdAndFilterTypeOrderByUpdatedAtDesc(userId, UserFilterType.ALERTS))
            .thenReturn(List.of(filter));

        List<UserFilterDTO> result = userFilterService.getAllFilters(userId, "ALERTS");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Repeated Hits Alerts");
        assertThat(result.getFirst().filterType()).isEqualTo(UserFilterType.ALERTS);
    }

    @Test
    void shouldCreateFilterForUserWithDefaultFlowType() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(userFilterRepository.save(any(UserFilter.class))).thenAnswer(invocation -> {
            UserFilter saved = invocation.getArgument(0);
            saved.setId(7L);
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        UserFilterDTO result = userFilterService.createFilter(
            userId,
            new CreateUserFilterRequest("My Flow", "{}", null)
        );

        ArgumentCaptor<UserFilter> captor = ArgumentCaptor.forClass(UserFilter.class);
        verify(userFilterRepository).save(captor.capture());

        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().getFilterType()).isEqualTo(UserFilterType.FLOW);
        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.filterType()).isEqualTo(UserFilterType.FLOW);
    }

    @Test
    void shouldRejectUnsupportedFilterType() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> userFilterService.getAllFilters(userId, "mystery"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Unsupported filter type");
    }

    @Test
    void shouldDeleteOnlyOwnedFilter() {
        UUID userId = UUID.randomUUID();
        UserFilter filter = new UserFilter();
        filter.setId(3L);

        when(userFilterRepository.findByIdAndUser_Id(3L, userId)).thenReturn(Optional.of(filter));

        userFilterService.deleteFilter(userId, 3L);

        verify(userFilterRepository).delete(filter);
    }
}
