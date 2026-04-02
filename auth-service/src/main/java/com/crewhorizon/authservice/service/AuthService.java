package com.crewhorizon.authservice.service;

import com.crewhorizon.authservice.dto.request.LoginRequest;
import com.crewhorizon.authservice.dto.request.RegisterRequest;
import com.crewhorizon.authservice.dto.response.AuthResponse;

/**
 * Auth Service Interface.
 *
 * WHY define a Service Interface:
 * 1. TESTABILITY: Tests inject mocks of the interface, not the impl.
 *    This keeps unit tests fast and isolated.
 * 2. DEPENDENCY INVERSION PRINCIPLE (DIP): Controllers depend on
 *    the abstraction (interface), not the implementation.
 *    Swapping implementations (e.g., LDAP-based auth) requires
 *    zero changes to the controller.
 * 3. AOP PROXYING: Spring AOP wraps interface-based proxies by
 *    default (vs class-based CGLIB proxies). Interface approach is
 *    cleaner and avoids proxy-related pitfalls.
 */
public interface AuthService {
    AuthResponse login(LoginRequest request);
    AuthResponse register(RegisterRequest request);
    AuthResponse refreshToken(String refreshToken);
    void logout(String accessToken);
    void changePassword(String email, String currentPassword, String newPassword);
}
