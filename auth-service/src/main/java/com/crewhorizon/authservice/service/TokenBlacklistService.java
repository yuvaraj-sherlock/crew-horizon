package com.crewhorizon.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * ============================================================
 * Token Blacklist Service
 * ============================================================
 * WHAT: Manages a Redis-backed blacklist of revoked JWT tokens.
 *       Tokens are added on logout or forced revocation and
 *       checked on every authenticated request.
 *
 * WHY Redis for Token Blacklist (not DB):
 *       1. PERFORMANCE: Redis is an in-memory store with O(1)
 *          GET operations — nanosecond latency vs milliseconds
 *          for PostgreSQL. Security checks on every request
 *          MUST be fast.
 *       2. AUTOMATIC EXPIRY: Redis TTL (Time-To-Live) automatically
 *          removes entries when the token would have expired anyway.
 *          No cleanup job needed — zero operational overhead.
 *       3. DISTRIBUTED: All auth-service replicas share the same
 *          Redis instance — a token blacklisted on pod 1 is
 *          immediately blacklisted on pods 2, 3, N.
 *
 * WHY store jti (token ID) not the full token:
 *       JWT tokens can be hundreds of bytes. Storing just the
 *       36-byte UUID (jti) reduces Redis memory by ~10x.
 *       The jti uniquely identifies the token with the same
 *       effectiveness as storing the full token string.
 *
 * WHY prefix "blacklist:":
 *       Namespacing Redis keys prevents collisions with other
 *       caches (crew data, flight data, etc.) stored in the
 *       same Redis instance. Convention: service:type:identifier
 * ============================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String BLACKLIST_PREFIX = "auth:blacklist:";

    /**
     * Adds a token to the blacklist.
     *
     * @param tokenId  JWT ID (jti claim) — unique identifier for the token
     * @param ttl      remaining validity duration (token should expire from
     *                 blacklist at the same time it would naturally expire)
     */
    public void blacklistToken(String tokenId, Duration ttl) {
        String key = BLACKLIST_PREFIX + tokenId;
        // WHY store "revoked" as value (not empty string):
        // Provides human-readable context when inspecting Redis in debugging.
        // Empty strings work too, but a meaningful value aids ops teams.
        redisTemplate.opsForValue().set(key, "revoked", ttl);
        log.info("Token blacklisted: jti={}, ttl={}", tokenId, ttl);
    }

    /**
     * Checks if a token is blacklisted.
     *
     * WHY return false on Redis error (fail-open for blacklist):
     * A Redis failure should NOT lock out all users. The tradeoff:
     * revoked tokens MIGHT work briefly if Redis is down.
     * This is acceptable because:
     * - Revocations are rare (logout events)
     * - Redis downtime should be seconds (K8s liveness probe restarts)
     * - The alternative (fail-closed) would be a complete outage
     *
     * @param tokenId JWT ID (jti claim)
     * @return true if token is blacklisted (revoked)
     */
    public boolean isBlacklisted(String tokenId) {
        try {
            String key = BLACKLIST_PREFIX + tokenId;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("Redis error checking blacklist for jti={}: {}",
                    tokenId, e.getMessage());
            return false; // Fail-open: prefer availability over strict revocation
        }
    }
}
