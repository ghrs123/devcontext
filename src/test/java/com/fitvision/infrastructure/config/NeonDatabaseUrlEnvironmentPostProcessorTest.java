package com.fitvision.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NeonDatabaseUrlEnvironmentPostProcessorTest {

    private final NeonDatabaseUrlEnvironmentPostProcessor processor = new NeonDatabaseUrlEnvironmentPostProcessor();

    @Test
    void splitsNeonUriIntoUrlUsernameAndPassword() {
        Map<String, Object> p = NeonDatabaseUrlEnvironmentPostProcessor.parse(
                "postgresql://neondb_owner:npg_secret@ep-x-pooler.eu-west-2.aws.neon.tech/neondb?sslmode=require&channel_binding=require");

        assertThat(p).containsEntry("spring.datasource.url",
                "jdbc:postgresql://ep-x-pooler.eu-west-2.aws.neon.tech/neondb?sslmode=require&channel_binding=require");
        assertThat(p).containsEntry("spring.datasource.username", "neondb_owner");
        assertThat(p).containsEntry("spring.datasource.password", "npg_secret");
    }

    @Test
    void keepsExplicitPort() {
        Map<String, Object> p = NeonDatabaseUrlEnvironmentPostProcessor.parse(
                "postgres://u:p@db.example.com:6543/app");

        assertThat(p).containsEntry("spring.datasource.url", "jdbc:postgresql://db.example.com:6543/app");
    }

    @Test
    void urlDecodesCredentials() {
        Map<String, Object> p = NeonDatabaseUrlEnvironmentPostProcessor.parse(
                "postgresql://user%40acme:p%40ss%2Fword@host/db");

        assertThat(p).containsEntry("spring.datasource.username", "user@acme");
        assertThat(p).containsEntry("spring.datasource.password", "p@ss/word");
    }

    @Test
    void noOpForPlainJdbcUrlWithoutCredentials() {
        assertThat(NeonDatabaseUrlEnvironmentPostProcessor.parse("jdbc:postgresql://host:5432/db?sslmode=require"))
                .isEmpty();
    }

    @Test
    void noOpForUnrecognisedScheme() {
        assertThat(NeonDatabaseUrlEnvironmentPostProcessor.parse("mysql://u:p@host/db")).isEmpty();
    }

    @Test
    void injectsOverridesWithoutRelyingOnActiveProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("DATABASE_URL", "postgresql://u:p@host.neon.tech/db?sslmode=require");

        processor.postProcessEnvironment(env, null);

        assertThat(env.getPropertySources().contains(NeonDatabaseUrlEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)).isTrue();
        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://host.neon.tech/db?sslmode=require");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("u");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("p");
    }

    @Test
    void noOpWhenDatabaseUrlAbsent() {
        MockEnvironment env = new MockEnvironment();

        processor.postProcessEnvironment(env, null);

        assertThat(env.getPropertySources().contains(NeonDatabaseUrlEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)).isFalse();
    }
}
