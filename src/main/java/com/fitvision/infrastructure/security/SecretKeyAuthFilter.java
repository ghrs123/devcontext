package com.fitvision.infrastructure.security;

import com.fitvision.domain.store.Store;
import com.fitvision.infrastructure.persistence.StoreRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Servlet filter that authenticates dashboard API requests using a store's secret API key.
 *
 * <p>Applied to all requests. For paths under {@code /api/dashboard/**} the filter reads the
 * {@code X-FitVision-Secret} header, looks up the corresponding {@link Store}, and — if the
 * store is found and {@code ACTIVE} — sets an {@link org.springframework.security.core.Authentication}
 * in the {@link SecurityContextHolder} with the {@link Store} as the principal.
 *
 * <p>If the header is absent, or the store is not found / not {@code ACTIVE}, no authentication
 * object is set. Spring Security's access-denied handling will reject the request with HTTP 401.
 *
 * <p>{@link TenantContext} is always cleared after the request completes (in a {@code finally}
 * block) to prevent memory leaks in thread-pool environments.
 *
 * <p>The filter is skipped entirely for {@code /api/widget/**} and {@code /actuator/**}.
 */
@Component
public class SecretKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SecretKeyAuthFilter.class);

    private static final String SECRET_KEY_HEADER = "X-FitVision-Secret";
    private static final String STORE_STATUS_ACTIVE = "ACTIVE";

    private final StoreRepository storeRepository;

    public SecretKeyAuthFilter(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/dashboard/v1/size-charts/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Only enforce secret key authentication for the dashboard surface
        if (!path.startsWith("/api/dashboard/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String secretKey = request.getHeader(SECRET_KEY_HEADER);

        if (secretKey == null || secretKey.isBlank()) {
            log.warn("Dashboard request to {} rejected: {} header is absent", path, SECRET_KEY_HEADER);
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Optional<Store> storeOpt = storeRepository.findByApiKeySecret(secretKey);

            if (storeOpt.isEmpty()) {
                log.warn("Dashboard request to {} rejected: secret key not found in store registry", path);
                log.debug("Secret key lookup result for path {}: no matching store", path);
                filterChain.doFilter(request, response);
                return;
            }

            Store store = storeOpt.get();
            log.debug("Secret key lookup result for path {}: store id={}, status={}", path,
                    store.getId(), store.getStatus());

            if (!STORE_STATUS_ACTIVE.equals(store.getStatus())) {
                log.warn("Dashboard request to {} rejected: store {} is not ACTIVE (status={})",
                        path, store.getId(), store.getStatus());
                filterChain.doFilter(request, response);
                return;
            }

            // Successful authentication — set principal and tenant context
            UsernamePasswordAuthenticationToken auth =
                    UsernamePasswordAuthenticationToken.authenticated(store, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
            TenantContext.set(store.getId());

            log.debug("Dashboard request authenticated for store id={}, tenantId={}",
                    store.getId(), store.getId());

            filterChain.doFilter(request, response);

        } finally {
            // Always clear — prevents ThreadLocal leaks in thread-pool environments
            TenantContext.clear();
        }
    }
}
