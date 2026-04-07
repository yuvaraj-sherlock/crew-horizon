package com.crewhorizon.crewservice.config;

import com.crewhorizon.crewservice.security.JwtRequestFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * ============================================================
 * Crew Service Security Configuration
 * ============================================================
 * WHAT: Configures Spring Security for the crew-service.
 *       Validates JWT tokens forwarded from the API Gateway.
 *
 * WHY security in EACH service (not just at the gateway):
 *       Defense in depth. If the API gateway is bypassed
 *       (internal traffic, misconfigured network policy),
 *       each service still enforces authentication independently.
 *       The header-based approach (X-Authenticated-User) is
 *       trusted ONLY from the gateway IP range, enforced via
 *       Kubernetes Network Policy.
 *
 * WHY @EnableMethodSecurity here:
 *       Enables @PreAuthorize on service methods like
 *       deleteCrewMember (HR_MANAGER only) and
 *       updateDutyStatus (SCHEDULER/OPERATIONS only).
 * ============================================================
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtRequestFilter jwtRequestFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/crew/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
