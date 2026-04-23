package com.fitvision.infrastructure.security;

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

    private static final ThreadLocal<UUID> HOLDER = new ThreadLocal<>();

    private TenantContext() {
        // Utility class — do not instantiate
    }

    /** Binds {@code tenantId} to the current thread. */
    public static void set(UUID tenantId) {
        HOLDER.set(tenantId);
    }

    /**
     * Returns the tenant ID bound to the current thread, or {@code null} if none
     * has been set (unauthenticated request).
     */
    public static UUID get() {
        return HOLDER.get();
    }

    /** Removes the tenant ID from the current thread. Always call in a {@code finally} block. */
    public static void clear() {
        HOLDER.remove();
    }
}
