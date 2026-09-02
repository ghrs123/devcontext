package com.fitvision.infrastructure.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Custom database health check measuring query latency.
 *
 * <p>Reports {@code DOWN} if the probe query fails or exceeds the configured
 * {@code fitvision.health.db.down-threshold-ms} (default 2000 ms). A successful
 * probe slower than {@code fitvision.health.db.slow-threshold-ms} (default
 * 100 ms) still reports {@code UP} but with a {@code warning} detail.
 *
 * <p>The down threshold is deliberately generous so that a cold-starting or
 * cross-region managed Postgres (e.g. Neon) does not flap the platform health
 * check into a restart loop. Lower it via configuration where the database is
 * co-located and expected to answer in single-digit milliseconds.
 */
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    private static final String HEALTH_QUERY = "SELECT 1";

    private final JdbcTemplate jdbcTemplate;
    private final long slowThresholdMs;
    private final long downThresholdMs;

    public DatabaseHealthIndicator(
            JdbcTemplate jdbcTemplate,
            @Value("${fitvision.health.db.slow-threshold-ms:100}") long slowThresholdMs,
            @Value("${fitvision.health.db.down-threshold-ms:2000}") long downThresholdMs) {
        this.jdbcTemplate = jdbcTemplate;
        this.slowThresholdMs = slowThresholdMs;
        this.downThresholdMs = downThresholdMs;
    }

    @Override
    public Health health() {
        long start = System.currentTimeMillis();
        try {
            jdbcTemplate.queryForObject(HEALTH_QUERY, Integer.class);
            long durationMs = System.currentTimeMillis() - start;

            if (durationMs > downThresholdMs) {
                return Health.down()
                        .withDetail("responseTimeMs", durationMs)
                        .withDetail("reason", "Database probe exceeded " + downThresholdMs + "ms threshold")
                        .build();
            }

            Health.Builder builder = Health.up().withDetail("responseTimeMs", durationMs);
            if (durationMs > slowThresholdMs) {
                builder.withDetail("warning", "Database probe exceeded " + slowThresholdMs + "ms");
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
