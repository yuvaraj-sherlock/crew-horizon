package com.crewhorizon.crewservice.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * ============================================================
 * Audit Logging AOP Aspect
 * ============================================================
 * WHAT: Cross-cutting aspect that automatically logs all
 *       service method calls with timing, user identity,
 *       and outcome (success/failure).
 *
 * WHY AOP for Logging (vs manual logging in each method):
 *       1. SEPARATION OF CONCERNS: Business logic (scheduling,
 *          crew management) is SEPARATE from audit concerns.
 *          Methods focus on what they do, not on logging it.
 *
 *       2. CONSISTENCY: Every service method gets the SAME
 *          logging behavior. No developer can accidentally
 *          skip audit logging on a sensitive operation.
 *
 *       3. DRY PRINCIPLE: Audit logic written once in the aspect,
 *          applied to hundreds of methods via pointcut expressions.
 *
 *       4. COMPLIANCE: Aviation industry regulations require
 *          comprehensive audit trails. AOP ensures complete
 *          coverage without burdening the development team.
 *
 * WHY @Around (vs @Before + @After):
 *       @Around captures BOTH the pre/post state AND the execution
 *       time in a single method. @Before + @After would require
 *       storing start time in a thread-local, which is messy.
 *
 * WHY pointcut on "com.crewhorizon.crewservice.service":
 *       We audit the SERVICE layer, not the controller layer.
 *       Service layer calls represent actual business operations.
 *       A controller call might result in 0 or N service calls.
 *       Auditing at the service layer captures actual data changes.
 * ============================================================
 */
@Slf4j
@Aspect
@Component
public class AuditLoggingAspect {

    /**
     * Pointcut: all public methods in any class within the
     * service package (including sub-packages).
     *
     * WHY "execution(public * ...)" not just "within(...)":
     * "within" matches ALL methods including private.
     * "execution(public *)" correctly targets only public methods —
     * the externally visible API of the service.
     */
    @Pointcut("execution(public * com.crewhorizon.crewservice.service..*(..))")
    public void serviceLayerMethods() {}

    /**
     * Pointcut: all controller methods (for HTTP request auditing).
     */
    @Pointcut("execution(public * com.crewhorizon.crewservice.controller..*(..))")
    public void controllerLayerMethods() {}

    /**
     * Around advice for service methods: logs entry, exit, and timing.
     *
     * WHY log method arguments:
     * For audit compliance, we need to know WHAT was requested,
     * not just that a method was called. Log arguments at DEBUG
     * level (can be enabled in production for incident investigation).
     *
     * WHY truncate args in production:
     * Some service methods receive large request objects.
     * Logging full objects could expose PII or fill disk space.
     * Truncation balances auditability with performance/privacy.
     */
    @Around("serviceLayerMethods()")
    public Object auditServiceCall(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String currentUser = getCurrentUsername();

        log.debug("[AUDIT-START] {}.{}() | user={} | args={}",
                className, methodName, currentUser,
                truncateArgs(joinPoint.getArgs()));

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;

            log.info("[AUDIT-SUCCESS] {}.{}() | user={} | duration={}ms",
                    className, methodName, currentUser, duration);

            // Warn on slow operations
            if (duration > 1000) {
                log.warn("[SLOW-OPERATION] {}.{}() took {}ms (threshold: 1000ms)",
                        className, methodName, duration);
            }

            return result;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            /*
             * WHY log exception type and message (not full stack trace here):
             * Full stack traces are logged by the GlobalExceptionHandler
             * for HTTP requests. Logging them again here would double the
             * log volume. We log the exception class and message for
             * quick identification, full trace is available at error level
             * from the handler.
             */
            log.warn("[AUDIT-FAILURE] {}.{}() | user={} | duration={}ms | exception={}: {}",
                    className, methodName, currentUser, duration,
                    e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }

    /**
     * Dedicated audit for data-mutating operations.
     * @Before ensures we log the INTENT to modify data, even if
     * the transaction later rolls back.
     */
    @Before("execution(* com.crewhorizon.crewservice.service..create*(..)) || " +
            "execution(* com.crewhorizon.crewservice.service..update*(..)) || " +
            "execution(* com.crewhorizon.crewservice.service..delete*(..))")
    public void auditDataModification(JoinPoint joinPoint) {
        String operation = joinPoint.getSignature().getName().replaceAll("([A-Z])", " $1").trim();
        String currentUser = getCurrentUsername();
        log.info("[AUDIT-MUTATION] Operation='{}' | user={} | args={}",
                operation, currentUser, truncateArgs(joinPoint.getArgs()));
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "system"; // For programmatic/scheduled calls
    }

    private String truncateArgs(Object[] args) {
        if (args == null || args.length == 0) return "[]";
        // Truncate to first 100 chars per arg to prevent log flooding
        return Arrays.stream(args)
                .map(arg -> {
                    if (arg == null) return "null";
                    String str = arg.toString();
                    return str.length() > 100 ? str.substring(0, 100) + "..." : str;
                })
                .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
    }
}
