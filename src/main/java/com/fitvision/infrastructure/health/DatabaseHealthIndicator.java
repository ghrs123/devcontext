package com.fitvision.infrastructure.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Custom database health check measuring query latency.
 *
 * <p>Reports {@code DOWN} if the probe query fails or exceeds 200 ms.
 */
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    private static final String HEALTH_QUERY = "SELECT 1";
    private static final long SLOW_THRESHOLD_MS = 100;
    private static final long DOWN_THRESHOLD_MS = 200;

    private final JdbcTemplate jdbcTemplate;

    public DatabaseHealthIndicator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Health health() {
        long start = System.currentTimeMillis();
        try {
            jdbcTemplate.queryForObject(HEALTH_QUERY, Integer.class);
            long durationMs = System.currentTimeMillis() - start;

            if (durationMs > DOWN_THRESHOLD_MS) {
                return Health.down()
                        .withDetail("responseTimeMs", durationMs)
                        .withDetail("reason", "Database probe exceeded 200ms threshold")
                        .build();
            }

            Health.Builder builder = Health.up().withDetail("responseTimeMs", durationMs);
            if (durationMs > SLOW_THRESHOLD_MS) {
                builder.withDetail("warning", "Database probe exceeded 100ms");
            }
            return builder.build();
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - start;
            return Health.down(ex)
                    .withDetail("responseTimeMs", durationMs)
                    .build();
        }
    }
}
