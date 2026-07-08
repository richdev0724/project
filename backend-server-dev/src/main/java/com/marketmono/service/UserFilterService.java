package com.marketmono.service;

import com.marketmono.dto.CreateUserFilterRequest;
import com.marketmono.dto.UpdateUserFilterRequest;
import com.marketmono.dto.UserFilterDTO;
import com.marketmono.entity.UserFilter;
import com.marketmono.entity.UserFilterType;
import com.marketmono.repository.UserFilterRepository;
import com.marketmono.repository.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserFilterService {
    
    private final UserFilterRepository userFilterRepository;
    private final UserRepository userRepository;
    
    @Transactional(readOnly = true)
    public List<UserFilterDTO> getAllFilters(UUID userId, String filterType) {
        UserFilterType resolvedFilterType = resolveFilterType(filterType);
        return userFilterRepository.findByUser_IdAndFilterTypeOrderByUpdatedAtDesc(userId, resolvedFilterType).stream()
            .map(this::toDTO)
            .toList();
    }
    
    @Transactional
    public UserFilterDTO createFilter(UUID userId, CreateUserFilterRequest request) {
        UserFilter filter = new UserFilter();
        filter.setName(request.name());
        filter.setUser(userRepository.getReferenceById(userId));
        filter.setFilterType(resolveFilterType(request.filterType()));
        filter.setFilterQuery(request.filterQuery());
        
        UserFilter saved = userFilterRepository.save(filter);
        log.info("Created {} filter '{}' with ID {} for user {}", saved.getFilterType(), saved.getName(), saved.getId(), userId);
        
        return toDTO(saved);
    }
    
    @Transactional
    public UserFilterDTO updateFilter(UUID userId, Long id, UpdateUserFilterRequest request) {
        UserFilter filter = userFilterRepository.findByIdAndUser_Id(id, userId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User filter not found with id: " + id));
        
        filter.setFilterQuery(request.filterQuery());
        if (request.filterType() != null) {
            filter.setFilterType(resolveFilterType(request.filterType()));
        }
        
        UserFilter updated = userFilterRepository.save(filter);
        log.info("Updated user filter {} for user {}", updated.getId(), userId);
        
        return toDTO(updated);
    }
    
    @Transactional
    public void deleteFilter(UUID userId, Long id) {
        UserFilter filter = userFilterRepository.findByIdAndUser_Id(id, userId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User filter not found with id: " + id));
        
        userFilterRepository.delete(filter);
        log.info("Deleted user filter {} for user {}", id, userId);
    }
    
    private UserFilterDTO toDTO(UserFilter filter) {
        return new UserFilterDTO(
            filter.getId(),
            filter.getName(),
            filter.getFilterType(),
            filter.getFilterQuery(),
            filter.getUpdatedAt()
        );
    }

    private UserFilterType resolveFilterType(String filterType) {
        if (filterType == null || filterType.isBlank()) {
            return UserFilterType.FLOW;
        }

        try {
            return UserFilterType.valueOf(filterType.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported filter type: " + filterType, exception);
        }
    }
}
