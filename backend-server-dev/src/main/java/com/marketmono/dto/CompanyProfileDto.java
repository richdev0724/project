package com.marketmono.dto;

public record CompanyProfileDto(
    String companyName,
    String description,
    String industry,
    String sector,
    String ceo,
    String website,
    Integer employees,
    Long marketCap,
    String country,
    String city,
    String state,
    String address,
    String phone,
    String exchange,
    String currency,
    Boolean isEtf,
    Boolean isActivelyTrading
) {}
