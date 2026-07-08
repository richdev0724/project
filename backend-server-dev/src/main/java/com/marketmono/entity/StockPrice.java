package com.marketmono.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(
    name = "stock_prices",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_stock_prices_ticker_datetime",
            columnNames = {"ticker", "price_date", "price_time"}
        )
    },
    indexes = {
        @Index(name = "idx_stock_prices_ticker_date", columnList = "ticker, price_date DESC"),
        @Index(name = "idx_stock_prices_date", columnList = "price_date DESC"),
        @Index(name = "idx_stock_prices_ticker_time", columnList = "ticker, price_date, price_time")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockPrice {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "ticker", nullable = false, length = 20)
    private String ticker;
    
    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;
    
    @Column(name = "price_time", nullable = false)
    private LocalTime priceTime;
    
    @Column(name = "open", precision = 12, scale = 4)
    private BigDecimal open;
    
    @Column(name = "high", precision = 12, scale = 4)
    private BigDecimal high;
    
    @Column(name = "low", precision = 12, scale = 4)
    private BigDecimal low;
    
    @Column(name = "close", nullable = false, precision = 12, scale = 4)
    private BigDecimal close;
    
    @Column(name = "volume")
    private Long volume;
    
    @Column(name = "vwap", precision = 12, scale = 4)
    private BigDecimal vwap;
    
    @Column(name = "transactions")
    private Integer transactions;
    
    @Builder.Default
    @Column(name = "source", nullable = false, length = 20)
    private String source = "MARKET_DATA";
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
