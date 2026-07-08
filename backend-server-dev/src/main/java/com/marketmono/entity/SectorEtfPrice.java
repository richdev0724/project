package com.marketmono.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sector_etf_prices",
    uniqueConstraints = @UniqueConstraint(name = "uk_sector_etf_ticker_date", columnNames = {"ticker", "date"}),
    indexes = @Index(name = "idx_sector_etf_ticker_date", columnList = "ticker, date DESC")
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class SectorEtfPrice {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "ticker", nullable = false, length = 10)
    private String ticker;
    
    @Column(name = "date", nullable = false)
    private LocalDate date;
    
    @Column(name = "close_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal closePrice;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
