package com.fitvision.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fitvision.shared.exception.ErrorCode;
import com.fitvision.shared.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration for the FitVision backend.
 *
 * <ul>
 *   <li>{@code /api/widget/**} — requires a valid store API key (enforced by
 *       {@link ApiKeyAuthFilter}).</li>
 *   <li>{@code /api/dashboard/**} — permit all for now; JWT filter added in Phase 5.</li>
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

    private final ApiKeyAuthFilter apiKeyAuthFilter;
    private final SecretKeyAuthFilter secretKeyAuthFilter;

    public SecurityConfig(ApiKeyAuthFilter apiKeyAuthFilter,
                          SecretKeyAuthFilter secretKeyAuthFilter) {
        this.apiKeyAuthFilter = apiKeyAuthFilter;
        this.secretKeyAuthFilter = secretKeyAuthFilter;
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
                        .requestMatchers("/api/dashboard/**").authenticated()
                        .requestMatchers("/api/widget/**").authenticated()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            String path = request.getRequestURI();
                            ErrorCode code = path.startsWith("/api/dashboard/")
                                    ? ErrorCode.INVALID_SECRET_KEY
                                    : ErrorCode.INVALID_API_KEY;
                            String message = path.startsWith("/api/dashboard/")
                                    ? "Missing or invalid secret key. Provide a valid X-FitVision-Secret header."
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
                .addFilterBefore(secretKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS configuration for widget and dashboard API surfaces.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration widgetCors = new CorsConfiguration();
        widgetCors.setAllowedOriginPatterns(List.of("*"));
        widgetCors.setAllowedMethods(List.of("POST", "OPTIONS"));
        widgetCors.setAllowedHeaders(List.of("X-FitVision-Key", "Content-Type"));
        widgetCors.setMaxAge(3600L);

        CorsConfiguration dashboardCors = new CorsConfiguration();
        dashboardCors.setAllowedOriginPatterns(List.of("*"));
        dashboardCors.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        dashboardCors.setAllowedHeaders(List.of("X-FitVision-Secret", "Content-Type"));
        dashboardCors.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/widget/**", widgetCors);
        source.registerCorsConfiguration("/api/dashboard/**", dashboardCors);
        return source;
    }
}
