package com.fitvision;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for all FitVision integration tests.
 *
 * <p>Starts a single PostgreSQL Testcontainer (shared across all subclasses via the
 * static {@code @Container} field). Flyway migrations run automatically on the first
 * application context startup and are not repeated on subsequent tests in the same
 * JVM run.
 *
 * <p>Subclasses must manage test data isolation themselves — typically via
 * {@code @BeforeEach} setup and {@code @AfterEach} teardown.
 *
 * <p>The {@code test} Spring profile is activated, which loads
 * {@code application-test.yml}. The datasource URL, username and password are
 * overridden at runtime by {@link #overrideDataSourceProperties}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("fitvision")
                    .withUsername("fitvision")
                    .withPassword("fitvision");

    /**
     * Injects the Testcontainer's dynamic JDBC coordinates into the Spring
     * {@code Environment} before the application context is started.
     *
     * <p>These properties override the static values in {@code application-test.yml}
     * so that JPA, Flyway and HikariCP all connect to the same container.
     */
    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
