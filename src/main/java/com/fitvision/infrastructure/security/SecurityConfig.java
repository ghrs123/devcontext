package com.fitvision.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fitvision.shared.exception.ErrorCode;
import com.fitvision.shared.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security configuration for the FitVision backend.
 *
 * <ul>
 *   <li>{@code /api/widget/**} — requires a valid store API key (enforced by
 *       {@link ApiKeyAuthFilter}).</li>
 *   <li>{@code /api/dashboard/v1/auth/**} — permit all (registration + login).</li>
 *   <li>{@code /api/dashboard/**} — requires a valid JWT (enforced by
 *       {@link JwtAuthFilter}).</li>
 *   <li>{@code /actuator/health} — permit all.</li>
 *   <li>CSRF disabled; sessions stateless.</li>
 * </ul>
 *
 * <p>Unauthenticated requests to {@code /api/widget/**} receive an HTTP 401 with the standard
 * {@link ApiResponse} envelope containing error code {@code INVALID_API_KEY}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

        private static final String OPTIONS = "OPTIONS";
        private static final String CONTENT_TYPE = "Content-Type";

    private static final List<String> PRODUCTION_CORS_ORIGINS = List.of(
            "https://app.fitvision.io",
            "https://fitvision.io",
            "https://*.myshopify.com"
    );

    private final ApiKeyAuthFilter apiKeyAuthFilter;
        private final JwtAuthFilter jwtAuthFilter;
        private final AdminAuthFilter adminAuthFilter;
        private final Environment environment;

    public SecurityConfig(ApiKeyAuthFilter apiKeyAuthFilter,
                                                  JwtAuthFilter jwtAuthFilter,
                                                  AdminAuthFilter adminAuthFilter,
                                                  Environment environment) {
        this.apiKeyAuthFilter = apiKeyAuthFilter;
                this.jwtAuthFilter = jwtAuthFilter;
                this.adminAuthFilter = adminAuthFilter;
                this.environment = environment;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/admin/seed").permitAll()
                        .requestMatchers("/api/shopify/**").permitAll()
                        .requestMatchers("/api/billing/webhooks").permitAll()
                        .requestMatchers("/api/dashboard/v1/auth/**").permitAll()
                        .requestMatchers("/api/admin/**").authenticated()
                        .requestMatchers("/api/dashboard/**").authenticated()
                        .requestMatchers("/api/widget/**").authenticated()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            String path = request.getRequestURI();
                            boolean jwtPath = path.startsWith("/api/dashboard/") || path.startsWith("/api/admin/");
                            ErrorCode code = jwtPath
                                    ? ErrorCode.UNAUTHORIZED
                                    : ErrorCode.INVALID_API_KEY;
                            String message = jwtPath
                                    ? "Missing or invalid JWT. Provide Authorization: Bearer <token>."
                                    : "Missing or invalid API key. Provide a valid X-FitVision-Key header.";
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            ApiResponse<Void> body = ApiResponse.error(code, message);
                            ObjectMapper mapper = new ObjectMapper();
                            mapper.registerModule(new JavaTimeModule());
                            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                            response.getWriter().write(mapper.writeValueAsString(body));
                        })
                )
                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(adminAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder(12);
        }

    /**
     * CORS configuration for widget and dashboard API surfaces.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        boolean production = Arrays.stream(environment.getActiveProfiles())
                .anyMatch("prod"::equals);
        List<String> restrictedOrigins = production ? PRODUCTION_CORS_ORIGINS : List.of("*");

        CorsConfiguration widgetCors = new CorsConfiguration();
        widgetCors.setAllowedOriginPatterns(List.of("*"));
        widgetCors.setAllowedMethods(List.of("POST", OPTIONS));
        widgetCors.setAllowedHeaders(List.of("X-FitVision-Key", CONTENT_TYPE));
        widgetCors.setMaxAge(3600L);

        CorsConfiguration dashboardCors = new CorsConfiguration();
        dashboardCors.setAllowedOriginPatterns(restrictedOrigins);
        dashboardCors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", OPTIONS));
        dashboardCors.setAllowedHeaders(List.of("Authorization", CONTENT_TYPE));
        dashboardCors.setMaxAge(3600L);

        CorsConfiguration adminCors = new CorsConfiguration();
        adminCors.setAllowedOriginPatterns(restrictedOrigins);
        adminCors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", OPTIONS));
        adminCors.setAllowedHeaders(List.of("Authorization", CONTENT_TYPE));
        adminCors.setMaxAge(3600L);

        CorsConfiguration shopifyCors = new CorsConfiguration();
        shopifyCors.setAllowedOriginPatterns(restrictedOrigins);
        shopifyCors.setAllowedMethods(List.of("GET", "POST", OPTIONS));
        shopifyCors.setAllowedHeaders(List.of("X-FitVision-Shopify-Secret", CONTENT_TYPE));
        shopifyCors.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/widget/**", widgetCors);
        source.registerCorsConfiguration("/api/dashboard/**", dashboardCors);
        source.registerCorsConfiguration("/api/admin/**", adminCors);
        source.registerCorsConfiguration("/api/shopify/**", shopifyCors);
        return source;
    }
}
