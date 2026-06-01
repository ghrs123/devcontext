package com.fitvision.infrastructure.security;

import com.fitvision.domain.store.Store;
import com.fitvision.infrastructure.persistence.StoreRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Servlet filter that authenticates widget API requests using a store's public API key.
 *
 * <p>Applied to all requests. For paths under {@code /api/widget/**} the filter reads the
 * {@code X-FitVision-Key} header, looks up the corresponding {@link Store}, and — if the
 * store is found and {@code ACTIVE} — sets an {@link org.springframework.security.core.Authentication}
 * in the {@link SecurityContextHolder} with the {@link Store} as the principal.
 *
 * <p>If the header is absent, or the store is not found / not {@code ACTIVE}, no authentication
 * object is set. Spring Security's access-denied handling will reject the request with HTTP 401.
 *
 * <p>{@link TenantContext} is always cleared after the request completes (in a {@code finally}
 * block) to prevent memory leaks in thread-pool environments.
 *
 * <p>The filter is skipped entirely for {@code /api/dashboard/**} and {@code /actuator/**}.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);

    private static final String API_KEY_HEADER = "X-FitVision-Key";
    private static final String STORE_STATUS_ACTIVE = "ACTIVE";

    private final StoreRepository storeRepository;

    public ApiKeyAuthFilter(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    /**
     * Skip the filter entirely for dashboard and actuator paths — those are handled by
     * separate mechanisms (JWT in Phase 5; Spring Boot Actuator respectively).
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/dashboard/") || path.startsWith("/api/admin/") || path.startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Only enforce API key authentication for the widget surface
        if (!path.startsWith("/api/widget/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Widget request to {} rejected: {} header is absent", path, API_KEY_HEADER);
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Optional<Store> storeOpt = storeRepository.findByApiKeyPublic(apiKey);

            if (storeOpt.isEmpty()) {
                log.warn("Widget request to {} rejected: API key not found in store registry", path);
                log.debug("API key lookup result for path {}: no matching store", path);
                filterChain.doFilter(request, response);
                return;
            }

            Store store = storeOpt.get();
            log.debug("API key lookup result for path {}: store id={}, status={}", path,
                    store.getId(), store.getStatus());

            if (!STORE_STATUS_ACTIVE.equals(store.getStatus())) {
                log.warn("Widget request to {} rejected: store {} is not ACTIVE (status={})",
                        path, store.getId(), store.getStatus());
                filterChain.doFilter(request, response);
                return;
            }

            // Successful authentication — set principal and tenant context
            UsernamePasswordAuthenticationToken auth =
                    UsernamePasswordAuthenticationToken.authenticated(store, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
            TenantContext.set(store.getId());

            log.debug("Widget request authenticated for store id={}, tenantId={}",
                    store.getId(), store.getId());

            filterChain.doFilter(request, response);

        } finally {
            // Always clear — prevents ThreadLocal leaks in thread-pool environments
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }
}
