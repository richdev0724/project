package com.marketmono.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response from Congress.gov API for member data
 * API: https://api.congress.gov/v3/member
 */
public record CongressGovMemberResponse(
    List<CongressMember> members
) {}
