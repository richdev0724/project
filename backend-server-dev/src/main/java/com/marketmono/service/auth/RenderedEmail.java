package com.marketmono.service.auth;

public record RenderedEmail(
    String subject,
    String htmlBody,
    String textBody
) {}
