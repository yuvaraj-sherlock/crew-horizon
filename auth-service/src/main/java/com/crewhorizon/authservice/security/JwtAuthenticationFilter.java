package com.crewhorizon.authservice.security;

import com.crewhorizon.authservice.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * ============================================================
 * JWT Authentication Filter (Auth Service)
 * ============================================================
 * WHAT: Validates JWT tokens for protected endpoints WITHIN the
 *       auth-service itself (e.g., user profile management,
 *       password change endpoints).
 *
 * WHY OncePerRequestFilter:
 *       Spring's filter chain can call filters multiple times
 *       per request (due to forward/include dispatches).
 *       OncePerRequestFilter guarantees execution EXACTLY ONCE
 *       per HTTP request — critical for security filters.
 *
 * WHY SecurityContextHolder.setContext (not setAuthentication):
 *       Spring Security v6 requires setting the full context
 *       for proper thread-local security propagation.
 *
 * WHY token blacklist check:
 *       JWT tokens are valid until expiry. If a user logs out
 *       or an admin revokes access, we cannot "un-issue" the
 *       token. The blacklist (stored in Redis) is checked here
 *       to honor explicit token revocations before expiry.
 * ============================================================
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractTokenFromRequest(request);

        if (StringUtils.hasText(token)) {
            try {
                Claims claims = jwtTokenProvider.validateAndExtractClaims(token);

                // WHY check token type: Refresh tokens must NOT be used as access tokens
                if (!jwtTokenProvider.isAccessToken(claims)) {
                    log.warn("Refresh token used as access token for path: {}",
                            request.getRequestURI());
                    filterChain.doFilter(request, response);
                    return;
                }

                // WHY check blacklist: Supports logout and forced token revocation
                String tokenId = jwtTokenProvider.extractTokenId(token);
                if (tokenBlacklistService.isBlacklisted(tokenId)) {
                    log.warn("Blacklisted token used: jti={}", tokenId);
                    filterChain.doFilter(request, response);
                    return;
                }

                String email = claims.getSubject();

                // Only authenticate if not already authenticated in this request
                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                    /*
                     * WHY create UsernamePasswordAuthenticationToken with 3 args:
                     * The 3-argument constructor marks the token as AUTHENTICATED.
                     * The 2-argument constructor marks it as UNAUTHENTICATED.
                     * This distinction is checked by Spring Security's
                     * authorization infrastructure.
                     */
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,  // credentials null after authentication
                                    userDetails.getAuthorities()
                            );
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("Authenticated user: {} for path: {}", email,
                            request.getRequestURI());
                }

            } catch (JwtException e) {
                log.debug("JWT validation failed: {}", e.getMessage());
                // Don't set authentication — Spring Security will handle the 401
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * WHY extract from Authorization header only (not query params):
     * JWT in query params appears in server logs, proxy logs, and
     * browser history — catastrophic for security. Always use headers.
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
