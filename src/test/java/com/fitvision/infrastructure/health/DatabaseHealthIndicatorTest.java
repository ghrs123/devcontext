package com.fitvision.infrastructure.health;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseHealthIndicatorTest {

    @Mock
    JdbcTemplate jdbcTemplate;

    @Test
    void reportsUpWhenProbeSucceedsQuickly() {
        when(jdbcTemplate.queryForObject(eq("SELECT 1"), eq(Integer.class))).thenReturn(1);

        Health health = new DatabaseHealthIndicator(jdbcTemplate, 100, 2000).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsDownWhenProbeThrows() {
        when(jdbcTemplate.queryForObject(eq("SELECT 1"), eq(Integer.class)))
                .thenThrow(new RuntimeException("connection refused"));

        Health health = new DatabaseHealthIndicator(jdbcTemplate, 100, 2000).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void reportsDownWhenProbeExceedsConfiguredDownThreshold() {
        // Zero threshold: any measurable latency trips DOWN, proving the bound is configurable.
        when(jdbcTemplate.queryForObject(eq("SELECT 1"), eq(Integer.class))).thenAnswer(inv -> {
            Thread.sleep(5);
            return 1;
        });

        Health health = new DatabaseHealthIndicator(jdbcTemplate, 0, 0).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("reason");
    }
}
