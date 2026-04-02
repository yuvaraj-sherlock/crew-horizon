package com.crewhorizon.authservice.config;

import com.crewhorizon.authservice.security.CustomUserDetailsService;
import com.crewhorizon.authservice.security.JwtAuthenticationEntryPoint;
import com.crewhorizon.authservice.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
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

/**
 * ============================================================
 * Spring Security Configuration
 * ============================================================
 * WHAT: Central security configuration for the auth-service.
 *       Defines the security filter chain, password encoding,
 *       authentication provider, and method-level security.
 *
 * WHY @EnableMethodSecurity (replacing @EnableGlobalMethodSecurity):
 *       Enables @PreAuthorize, @PostAuthorize, and @Secured
 *       annotations on controller/service methods. This is
 *       "defense in depth" — even if routing is misconfigured,
 *       method-level security provides a second barrier.
 *
 * WHY STATELESS session management:
 *       JWT-based auth means we NEVER store session state on
 *       the server. Each request is self-authenticated via the
 *       token. This enables horizontal scaling (any pod can
 *       handle any request) — fundamental to Kubernetes workloads.
 *
 * WHY disable CSRF:
 *       CSRF attacks exploit cookie-based session auth.
 *       Since we use JWT in Authorization headers (not cookies),
 *       CSRF tokens are unnecessary and add overhead.
 *       NOTE: If we ever use httpOnly cookies for refresh tokens,
 *       CSRF protection must be re-enabled for those endpoints.
 *
 * WHY BCrypt with strength 12:
 *       BCrypt strength 12 (~300ms per hash) is the industry
 *       recommendation for 2024. Slow enough to deter brute
 *       force, fast enough for normal auth workloads.
 *       Strength 10 is too fast; 14+ is too slow for API auth.
 * ============================================================
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // WHY disable CSRF: Stateless JWT auth doesn't need CSRF protection
                .csrf(AbstractHttpConfigurer::disable)

                // WHY disable form login: This is a REST API, not a web app
                .formLogin(AbstractHttpConfigurer::disable)

                // WHY disable HTTP Basic: JWT replaces HTTP Basic auth entirely
                .httpBasic(AbstractHttpConfigurer::disable)

                // Custom unauthorized response handler
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint))

                // WHY STATELESS: No HttpSession created or used — pure JWT
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // Public auth endpoints — no token required
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password"
                        ).permitAll()

                        // Actuator health/info public for K8s probes
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness",
                                "/actuator/info"
                        ).permitAll()

                        // OpenAPI docs — restrict in production via network policy
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // Admin-only endpoints
                        .requestMatchers("/api/v1/users/admin/**")
                                .hasRole("ADMIN")

                        // All other requests must be authenticated
                        .anyRequest().authenticated()
                )

                /*
                 * WHY place JWT filter BEFORE UsernamePasswordAuthenticationFilter:
                 * The standard Spring Security filter chain uses
                 * UsernamePasswordAuthenticationFilter for form-based auth.
                 * Our JWT filter needs to run first to set up the
                 * SecurityContext before any authorization checks occur.
                 */
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * WHY DaoAuthenticationProvider:
     * Connects Spring Security's AuthenticationManager to our
     * UserDetailsService and PasswordEncoder. When
     * authenticationManager.authenticate(token) is called, this
     * provider loads the user, encodes the provided password,
     * and compares it to the stored hash.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * WHY expose AuthenticationManager as a bean:
     * AuthenticationManager is needed in AuthService to
     * programmatically authenticate users. Without @Bean,
     * it can't be autowired outside the Security config.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * WHY BCryptPasswordEncoder(12):
     * Strength 12 provides ~300ms hash time in 2024 hardware.
     * This is intentionally slow to deter brute force attacks.
     * Never use MD5, SHA-1, or SHA-256 for passwords — they're
     * designed to be FAST, making brute force trivial.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
