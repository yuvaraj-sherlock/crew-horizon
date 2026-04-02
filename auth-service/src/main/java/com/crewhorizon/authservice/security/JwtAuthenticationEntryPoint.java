package com.crewhorizon.authservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * ============================================================
 * JWT Authentication Entry Point
 * ============================================================
 * WHAT: Custom handler called when an UNAUTHENTICATED request
 *       tries to access a protected resource.
 *
 * WHY custom AuthenticationEntryPoint:
 *       Spring Security's default handler returns an HTML 401 page.
 *       Our REST API consumers (mobile apps, web frontends) expect
 *       JSON responses. This entry point returns a structured
 *       JSON error response consistent with our error format.
 *
 * WHY 401 (not 403):
 *       401 Unauthorized = "Who are you?" (missing/invalid credentials)
 *       403 Forbidden = "I know who you are, but you can't do that"
 *       Accessing a protected endpoint without a token = 401.
 * ============================================================
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("Unauthorized access attempt to: {}", request.getRequestURI());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> errorBody = Map.of(
                "status", 401,
                "error", "Unauthorized",
                "message", "Authentication is required to access this resource",
                "path", request.getRequestURI(),
                "timestamp", Instant.now().toString()
        );

        objectMapper.writeValue(response.getOutputStream(), errorBody);
    }
}
