package com.marketmono.repository;

import com.marketmono.entity.Sector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SectorRepository extends JpaRepository<Sector, Integer> {
    Optional<Sector> findBySectorName(String sectorName);
}
