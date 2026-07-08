package com.marketmono.service.auth;

@FunctionalInterface
public interface GmailAccessTokenProvider {
    String getAccessToken();
}
