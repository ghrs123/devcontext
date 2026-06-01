package com.fitvision.infrastructure.security;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Thread-local holder for the authenticated store's tenant ID.
 *
 * Set by {@link ApiKeyAuthFilter} immediately after successful API key authentication.
 * Must always be cleared in a {@code finally} block after the request completes to
 * prevent memory leaks in thread-pool environments.
 *
 * Usage:
 * <pre>
 *   TenantContext.set(store.getId());
 *   try {
 *       chain.doFilter(request, response);
 *   } finally {
 *       TenantContext.clear();
 *   }
 * </pre>
 */
public final class TenantContext {

    public static final String MDC_TENANT_ID_KEY = "tenantId";

    private static final ThreadLocal<UUID> HOLDER = new ThreadLocal<>();

    private TenantContext() {
        // Utility class — do not instantiate
    }

    /** Binds {@code tenantId} to the current thread and MDC for structured logging. */
    public static void set(UUID tenantId) {
        HOLDER.set(tenantId);
        if (tenantId != null) {
            MDC.put(MDC_TENANT_ID_KEY, tenantId.toString());
        }
    }

    /**
     * Returns the tenant ID bound to the current thread, or {@code null} if none
     * has been set (unauthenticated request).
     */
    public static UUID get() {
        return HOLDER.get();
    }

    /** Removes the tenant ID from the current thread and MDC. Always call in a {@code finally} block. */
    public static void clear() {
        HOLDER.remove();
        MDC.remove(MDC_TENANT_ID_KEY);
    }
}
