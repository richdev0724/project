package com.marketmono.repository;

import com.marketmono.entity.HonorCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface HonorCodeRepository extends JpaRepository<HonorCode, UUID> {

    Optional<HonorCode> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM HonorCode h WHERE h.code = :code")
    Optional<HonorCode> findByCodeForUpdate(@Param("code") String code);
}
