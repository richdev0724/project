package com.marketmono.repository;

import com.marketmono.entity.UserFilter;
import com.marketmono.entity.UserFilterType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserFilterRepository extends JpaRepository<UserFilter, Long> {

	List<UserFilter> findByUser_IdAndFilterTypeOrderByUpdatedAtDesc(UUID userId, UserFilterType filterType);

	Optional<UserFilter> findByIdAndUser_Id(Long id, UUID userId);
}
