package com.marketmono.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a politician (Senator or House Representative)
 * Stores basic info synced from upstream market data API
 * 
 * Uses modern Hibernate 7.1 @JdbcTypeCode approach for PostgreSQL enum mapping
 * See: .vscode/docs/POSTGRESQL_ENUM_MAPPING_APPROACHES.md
 */
@Entity
@Table(name = "politicians")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Politician {
    
    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;
    
    @Column(nullable = false, length = 200)
    private String name;
    
    @Column(name = "is_active")
    private Boolean isActive;
    
    /**
     * Member type (HOUSE or SENATE)
     * Maps to PostgreSQL member_type enum
     * 
     * CRITICAL: columnDefinition is required for enum names with underscores
     * Without it, Hibernate validation fails with "wrong column type" error
     * 
     * @see MemberType
     * @see <a href="https://discourse.hibernate.org/t/named-enum-failing-validation-boot-sequence-hibernate-6-4/9088/13">Hibernate Discourse Solution</a>
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "member_type", columnDefinition = "member_type")
    private MemberType memberType;
    
    /**
     * Political party affiliation code
     * D = Democratic, R = Republican, I = Independent, UNK = Unknown
     */
    @Column(length = 3, nullable = false)
    private String party;
    
    /**
     * Timestamp when party affiliation was last updated
     */
    @Column(name = "party_updated_at")
    private LocalDateTime partyUpdatedAt;
}
