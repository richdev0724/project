package com.marketmono.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sectors", uniqueConstraints = @UniqueConstraint(columnNames = "sector_name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Sector {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(name = "sector_name", nullable = false, length = 100)
    private String sectorName;
    
    @Column(name = "industry", length = 100)
    private String industry;
    
    @Column(name = "min_market_cap")
    private Long minMarketCap;  // Optional: for sector-specific market cap ranges
    
    @Column(name = "max_market_cap")
    private Long maxMarketCap;  // Optional
    
    // Custom constructor for common use case
    public Sector(String sectorName, String industry) {
        this.sectorName = sectorName;
        this.industry = industry;
    }
}
