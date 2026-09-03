package com.fitvision.infrastructure.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Normalises a Neon / Heroku-style {@code DATABASE_URL} into the shape Spring Boot's
 * DataSource auto-configuration expects, before it runs.
 *
 * <p>Managed Postgres providers hand out a single URI of the form
 * {@code postgresql://user:password@host:port/db?sslmode=require}. The PostgreSQL JDBC
 * driver does <em>not</em> accept credentials in the URL authority, so this post-processor
 * splits it into:
 * <ul>
 *   <li>{@code spring.datasource.url} = {@code jdbc:postgresql://host:port/db?<query>}</li>
 *   <li>{@code spring.datasource.username} / {@code spring.datasource.password} (URL-decoded)</li>
 * </ul>
 *
 * <p>It only acts when {@code DATABASE_URL} is set (the dev and test profiles use
 * {@code DB_URL}, so this is a no-op there) and is idempotent for a value that is already a
 * plain {@code jdbc:postgresql://host...} URL with no embedded credentials. It does not gate
 * on the {@code prod} profile: at {@link Ordered#HIGHEST_PRECEDENCE} it runs before profile
 * activation is finalised, so {@code environment.getActiveProfiles()} is unreliable here.
 */
public class NeonDatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "neonDatabaseUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            databaseUrl = System.getenv("DATABASE_URL");
        }
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return;
        }

        Map<String, Object> properties = parse(databaseUrl.trim());
        if (properties.isEmpty()) {
            return;
        }
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
    }

    /**
     * Returns the {@code spring.datasource.*} overrides for the given raw URL, or an empty
     * map when nothing needs rewriting.
     */
    static Map<String, Object> parse(String raw) {
        Map<String, Object> out = new HashMap<>();

        String url = raw;
        if (url.startsWith("jdbc:")) {
            url = url.substring("jdbc:".length());
        }

        if (!url.startsWith("postgresql://") && !url.startsWith("postgres://")) {
            return out;
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception ex) {
            return out;
        }

        String host = uri.getHost();
        if (host == null) {
            return out;
        }
        int port = uri.getPort();
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String query = uri.getRawQuery();

        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(host);
        if (port != -1) {
            jdbc.append(':').append(port);
        }
        jdbc.append(path);
        if (query != null && !query.isBlank()) {
            jdbc.append('?').append(query);
        }
        out.put("spring.datasource.url", jdbc.toString());

        String userInfo = uri.getRawUserInfo();
        if (userInfo != null && !userInfo.isBlank()) {
            int colon = userInfo.indexOf(':');
            String user = colon >= 0 ? userInfo.substring(0, colon) : userInfo;
            String password = colon >= 0 ? userInfo.substring(colon + 1) : null;
            out.put("spring.datasource.username", URLDecoder.decode(user, StandardCharsets.UTF_8));
            if (password != null) {
                out.put("spring.datasource.password", URLDecoder.decode(password, StandardCharsets.UTF_8));
            }
        }

        // Nothing meaningful changed (already a clean jdbc URL, no credentials to extract).
        if (raw.startsWith("jdbc:") && out.size() == 1 && raw.equals(out.get("spring.datasource.url"))) {
            return new HashMap<>();
        }
        return out;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
