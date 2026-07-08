package com.marketmono.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a politician stock/option trade (Senate/House disclosure)
 * Stores trade data synced from upstream market data API
 * 
 * Uses modern Hibernate 7.1 @JdbcType approach for PostgreSQL enum mapping
 * See: .vscode/docs/POSTGRESQL_ENUM_MAPPING_APPROACHES.md
 */
@Entity
@Table(name = "politician_trades", indexes = {
    @Index(name = "idx_politician_id", columnList = "politician_id"),
    @Index(name = "idx_ticker", columnList = "ticker"),
    @Index(name = "idx_transaction_date", columnList = "transaction_date"),
    @Index(name = "idx_filed_at_date", columnList = "filed_at_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PoliticianTrade {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "politician_id", nullable = false, columnDefinition = "UUID")
    private UUID politicianId;
    
    /**
     * Transaction type (BUY, SELL, EXCHANGE)
     * Maps to PostgreSQL transaction_type enum
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "txn_type", columnDefinition = "transaction_type")
    private TransactionType txnType;
    
    @Column(length = 20)
    private String ticker;
    
    @Column(name = "transaction_date")
    private LocalDate transactionDate;
    
    @Column(name = "filed_at_date")
    private LocalDate filedAtDate;
    
    @Column(length = 100)
    private String amounts; // e.g., "$15,001 - $50,000"
    
    /**
     * Issuer/owner type (SELF, SPOUSE, CHILD, JOINT, UNDISCLOSED)
     * Maps to PostgreSQL issuer_type enum
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "issuer", columnDefinition = "issuer_type")
    private IssuerType issuer;
    
    /**
     * Member type (HOUSE or SENATE)
     * Maps to PostgreSQL member_type enum
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "member_type", columnDefinition = "member_type")
    private MemberType memberType;
    
    @Column(length = 200)
    private String reporter;
    
    @Column(name = "is_active")
    private Boolean isActive;
    
    @Column(columnDefinition = "TEXT")
    private String notes;
    
    @Column(name = "source", nullable = false, length = 20)
    private String source;
    
    @Column(name = "unique_key", nullable = false, length = 100)
    private String uniqueKey;
    
    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;
}
