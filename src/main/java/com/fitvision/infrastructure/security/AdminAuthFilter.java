package com.fitvision.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitvision.domain.store.Store;
import com.fitvision.domain.store.StoreRole;
import com.fitvision.infrastructure.persistence.StoreRepository;
import com.fitvision.shared.exception.ErrorCode;
import com.fitvision.shared.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class AdminAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthFilter.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final StoreRepository storeRepository;
    private final ObjectMapper objectMapper;

    public AdminAuthFilter(JwtService jwtService,
                           StoreRepository storeRepository,
                           ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.storeRepository = storeRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/admin/")) {
            return true;
        }
        return path.startsWith("/api/admin/seed");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        if (!jwtService.validateToken(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String role = jwtService.extractRole(token);
            if (!StoreRole.ADMIN.name().equals(role)) {
                log.warn("Admin endpoint forbidden: role={} path={}", role, request.getRequestURI());
                writeForbidden(response);
                return;
            }

            UUID adminStoreId = jwtService.extractStoreId(token);
            Optional<Store> storeOpt = storeRepository.findById(adminStoreId);
            if (storeOpt.isEmpty()) {
                log.warn("Admin endpoint forbidden: store in token not found id={}", adminStoreId);
                writeForbidden(response);
                return;
            }

            Store adminStore = storeOpt.get();
            UsernamePasswordAuthenticationToken auth =
                    UsernamePasswordAuthenticationToken.authenticated(adminStore, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
            TenantContext.set(adminStore.getId());

            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.error(ErrorCode.UNAUTHORIZED, "Admin role required.");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
