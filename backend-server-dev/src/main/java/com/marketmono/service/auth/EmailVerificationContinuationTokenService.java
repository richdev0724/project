package com.marketmono.service.auth;

import com.marketmono.config.security.JwtProperties;
import com.marketmono.dto.auth.EmailVerificationContinuationToken;
import com.marketmono.entity.auth.EmailVerificationToken;
import com.marketmono.entity.auth.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and validates short-lived same-browser continuation tokens for email verification auto-login.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationContinuationTokenService {

    private static final String PURPOSE = "email_verification_auto_login";
    private static final String PURPOSE_CLAIM = "purpose";
    private static final String EMAIL_VERIFICATION_TOKEN_HASH_CLAIM = "emailVerificationTokenHash";
    private static final long EXPIRATION_HOURS = 2L;

    private final JwtProperties jwtProperties;

    public EmailVerificationContinuationToken create(User user, EmailVerificationToken emailVerificationToken) {
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(EXPIRATION_HOURS);
        if (emailVerificationToken.getExpiresAt().isBefore(expiresAt)) {
            expiresAt = emailVerificationToken.getExpiresAt();
        }

        Date now = new Date();
        String token = Jwts.builder()
            .subject(user.getId().toString())
            .issuer(jwtProperties.getIssuer())
            .claim(PURPOSE_CLAIM, PURPOSE)
            .claim(EMAIL_VERIFICATION_TOKEN_HASH_CLAIM, hashToken(emailVerificationToken.getToken()))
            .issuedAt(now)
            .expiration(toDate(expiresAt))
            .signWith(getSigningKey())
            .compact();

        return new EmailVerificationContinuationToken(token, expiresAt);
    }

    public boolean matches(String continuationToken, User user, EmailVerificationToken emailVerificationToken) {
        if (continuationToken == null || continuationToken.isBlank() || user.getId() == null) {
            return false;
        }

        try {
            Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .requireIssuer(jwtProperties.getIssuer())
                .build()
                .parseSignedClaims(continuationToken)
                .getPayload();

            return PURPOSE.equals(claims.get(PURPOSE_CLAIM, String.class))
                && user.getId().equals(UUID.fromString(claims.getSubject()))
                && hashToken(emailVerificationToken.getToken())
                    .equals(claims.get(EMAIL_VERIFICATION_TOKEN_HASH_CLAIM, String.class));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Email verification continuation token was not accepted");
            return false;
        }
    }

    private SecretKey getSigningKey() {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is not configured");
        }

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes for HS256");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available", ex);
        }
    }

    private static Date toDate(LocalDateTime value) {
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }
}
