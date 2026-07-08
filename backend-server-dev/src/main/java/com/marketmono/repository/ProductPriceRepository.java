package com.marketmono.repository;

import com.marketmono.entity.ProductPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductPriceRepository extends JpaRepository<ProductPrice, UUID> {

    @Query("""
        SELECT pp
        FROM ProductPrice pp
        JOIN FETCH pp.product p
        WHERE pp.id = :id
        AND COALESCE(pp.isActive, false) = true
        AND COALESCE(p.isActive, false) = true
        AND COALESCE(p.isArchived, false) = false
        """)
    Optional<ProductPrice> findActiveByIdWithProduct(@Param("id") UUID id);
}
