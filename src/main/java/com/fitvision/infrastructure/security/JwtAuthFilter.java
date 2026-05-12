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
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String STORE_STATUS_ACTIVE = "ACTIVE";

    private final JwtService jwtService;
    private final StoreRepository storeRepository;

    public JwtAuthFilter(JwtService jwtService, StoreRepository storeRepository) {
        this.jwtService = jwtService;
        this.storeRepository = storeRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/widget/") || path.startsWith("/actuator/")) {
            return true;
        }
        if (!path.startsWith("/api/dashboard/")) {
            return true;
        }
        return path.startsWith("/api/dashboard/v1/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        if (!jwtService.validateToken(token)) {
            log.warn("Dashboard request rejected: invalid or expired JWT");
            filterChain.doFilter(request, response);
            return;
        }

        try {
            UUID storeId = jwtService.extractStoreId(token);
            Optional<Store> storeOpt = storeRepository.findById(storeId);

            if (storeOpt.isEmpty()) {
                log.warn("Dashboard request rejected: store from JWT does not exist");
                filterChain.doFilter(request, response);
                return;
            }

            Store store = storeOpt.get();
            if (!STORE_STATUS_ACTIVE.equals(store.getStatus())) {
                log.warn("Dashboard request rejected: store {} is not ACTIVE", store.getId());
                filterChain.doFilter(request, response);
                return;
            }

            UsernamePasswordAuthenticationToken auth =
                    UsernamePasswordAuthenticationToken.authenticated(store, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
            TenantContext.set(store.getId());

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
