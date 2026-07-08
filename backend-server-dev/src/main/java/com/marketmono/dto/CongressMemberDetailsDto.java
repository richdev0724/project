package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CongressMemberDetailsDto(
    String id,
    String bioguideId,
    String firstName,
    String lastName,
    String middleName,
    String suffix,
    String officialName,
    String party,
    String state,
    String stateCode,
    String chamber,
    String district,
    String town,
    String phone,
    String officeBuilding,
    String officeRoom,
    String website,
    Integer age,
    String imageUrl
) {}
