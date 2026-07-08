package com.marketmono.config.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration with JWT authentication.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties(InternalAdminApiProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final InternalAdminApiAuthenticationFilter internalAdminApiAuthenticationFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final PasswordChangeRequiredFilter passwordChangeRequiredFilter;
    private final CustomUserDetailsService userDetailsService;
    private final SubscriptionAccessFilter subscriptionAccessFilter;
    
    /**
     * Main security filter chain configuration.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for stateless JWT authentication
            .csrf(AbstractHttpConfigurer::disable)
            
            // Enable CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // SockJS iframe fallback is served from the backend and proxied through nginx,
            // which already sets X-Frame-Options: SAMEORIGIN. Use the same policy here to
            // avoid conflicting DENY/SAMEORIGIN headers that break the fallback transport.
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            
            // Stateless session management (no server-side sessions)
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
                )
            )
            
            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints (no authentication required)
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/registration/mode").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/registration-mode").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/waitlist").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/honor-codes/validate").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/actuator/prometheus").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/error").permitAll()
                
                // WebSocket STOMP handshake (SockJS uses HTTP — must be permitted)
                .requestMatchers("/ws/**", "/ws").permitAll()
                
                // Public local-data endpoints (no synchronous upstream market-data proxying)
                .requestMatchers(HttpMethod.GET, "/api/v1/ticker-bio-info/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/congress-member-details/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/changelog/**").permitAll()
                .requestMatchers(HttpMethod.GET,
                    "/api/v1/econ_events",
                    "/api/v1/econ_events/**").permitAll()
                
                // Product storefront (public - view available products)
                .requestMatchers(HttpMethod.GET, "/api/v1/products/storefront/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/products/summaries").permitAll()
                
                // Stripe status check (public - for UI to know if payments are enabled)
                .requestMatchers(HttpMethod.GET, "/api/v1/stripe/status").permitAll()
                
                // Stripe webhook (public - authenticated via signature verification)
                .requestMatchers(HttpMethod.POST, "/api/v1/stripe/webhook").permitAll()

                // Whop webhook (public - authenticated via Standard Webhooks signature verification)
                .requestMatchers(HttpMethod.POST, "/api/v1/whop/webhooks").permitAll()
                
                // Protected data endpoints (require authentication + active subscription via SubscriptionAccessFilter)
                .requestMatchers(HttpMethod.GET, "/api/v1/market-tide/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/flow/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/whale-trades/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/politicians/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/congress-recent-trades/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/news/**").authenticated()
                .requestMatchers(HttpMethod.GET,
                    "/api/v1/economic-calendar",
                    "/api/v1/economic-calendar/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/sector-performance/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/gamma-exposure/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/greek-exposure-by-strike/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/greek-exposure-strike-expiry/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/ticker-tide/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/top-net-impact/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/stock-state/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/stock/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/market-status/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/ticker-details/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/stock-information/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/pool/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/earnings/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/earnings-reports/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/screener/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/option-trades/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/options-volume/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/ticker-options-volume/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/darkpool-recent/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/dark-pool/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/darkpool/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/spot-exposure/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/spot-exposure-by-strike-expiry/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/volume-price-levels/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/ohlc/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/analyst-ratings/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/insider-trades/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/option-chains/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/expiry-breakdown/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/price-change-per-month/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/seasonality/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/options-price-levels/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/option-contract-historic/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/option-contract-intraday/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/option-contracts-all/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/iv-rank/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/realized-volatility/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/GEX-Strike-Expiry-Range/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/institution/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/etfs/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/rate-limit/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/darkLitReports/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/market-data/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/stock/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/darkpool/**").authenticated()
                
                // Subscription endpoints (require authentication)
                .requestMatchers("/api/v1/subscriptions/**").authenticated()
                .requestMatchers("/api/v1/stripe/public-key").authenticated()
                
                // Stripe Checkout (require authentication, for new subscriptions)
                .requestMatchers("/api/v1/stripe/checkout/**").authenticated()
                
                // Stripe Customer Portal (require authentication, subscription checked by filter)
                .requestMatchers("/api/v1/stripe/portal/**").authenticated()

                // Provider-neutral billing portal (require authentication, provider checked by service)
                .requestMatchers("/api/v1/billing/portal/**").authenticated()
                
                // Admin endpoints (require ADMIN role)
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                
                // User filter endpoints (require authentication)
                .requestMatchers("/api/v1/userfilter/**").authenticated()
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            )

            // Allow trusted backend-admin service calls for selected admin endpoints.
            // Anchor custom filters to Spring Security's built-in UsernamePasswordAuthenticationFilter
            // so filter ordering works reliably even when the custom JWT filter isn't registered yet.
            .addFilterBefore(internalAdminApiAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            
            // Add JWT filter immediately after the internal-admin pre-auth filter
            .addFilterAfter(jwtAuthenticationFilter, InternalAdminApiAuthenticationFilter.class)

            // Force invited users to complete first-login password setup before app access
            .addFilterAfter(passwordChangeRequiredFilter, JwtAuthenticationFilter.class)
            
            // Add subscription access filter after password-change enforcement
            .addFilterAfter(subscriptionAccessFilter, PasswordChangeRequiredFilter.class)
            
            // Use custom authentication provider
            .authenticationProvider(authenticationProvider());
        
        return http.build();
    }
    
    /**
     * CORS configuration for frontend.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "https://marketmono.com",
            "https://www.marketmono.com",
            "https://*.marketmono.com"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
    
    /**
     * Password encoder using BCrypt with strength 10.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
    
    /**
     * Authentication provider using custom UserDetailsService.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }
    
    /**
     * Authentication manager for manual authentication.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
