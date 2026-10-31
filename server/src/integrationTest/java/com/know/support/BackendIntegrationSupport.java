package com.know.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.know.KnowApplication;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
    classes = KnowApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.config.location=classpath:application-integration.yml")
@Import(BackendIntegrationSupport.AuthenticationFixture.class)
@ResourceLock("backend-integration-database")
public abstract class BackendIntegrationSupport {
  @Autowired protected com.know.categories.application.CategoryCatalog categoryFixtures;
  @Autowired protected TestClock entityTestClock;

  public static class TestClock extends java.time.Clock {
    private final java.util.concurrent.atomic.AtomicReference<java.time.Instant> time =
        new java.util.concurrent.atomic.AtomicReference<>(
            java.time.Instant.parse("2026-10-25T18:30:00Z"));

    public void set(java.time.Instant value) {
      time.set(value);
    }

    public java.time.Instant instant() {
      return time.get();
    }

    public java.time.ZoneId getZone() {
      return java.time.ZoneOffset.UTC;
    }

    public java.time.Clock withZone(java.time.ZoneId zone) {
      return java.time.Clock.fixed(instant(), zone);
    }
  }

  // JVM-scoped rather than JUnit class-scoped: a cached context must keep its database alive.
  // Testcontainers/Ryuk cleans up this disposable container when the test JVM exits.
  private static final PostgreSQLContainer POSTGRES = startDatabase();
  protected static final IdentificationProviderFixture IDENTIFICATION =
      new IdentificationProviderFixture();
  private static final AtomicReference<ApplicationContext> SHARED_CONTEXT = new AtomicReference<>();

  private static PostgreSQLContainer startDatabase() {
    var database =
        new PostgreSQLContainer(
            org.testcontainers.utility.DockerImageName.parse("pgvector/pgvector:0.8.7-pg17")
                .asCompatibleSubstituteFor("postgres"));
    database.start();
    return database;
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add(
        "know.identification.providers.openrouter.base-url",
        () -> IDENTIFICATION.url("openrouter"));
    registry.add("know.identification.providers.groq.base-url", () -> IDENTIFICATION.url("groq"));
  }

  @BeforeEach
  void verifyContextReuse(@Autowired ApplicationContext context) {
    SHARED_CONTEXT.compareAndSet(null, context);
    assertThat(context)
        .as("All backend ITs must reuse the same Spring context")
        .isSameAs(SHARED_CONTEXT.get());
  }

  @TestConfiguration
  public static class AuthenticationFixture {
    @Bean
    @org.springframework.context.annotation.Primary
    EmbeddingProviderFixture embeddingProviderFixture() {
      return new EmbeddingProviderFixture();
    }

    @Bean(destroyMethod = "close")
    ScreenshotProviderFixture screenshotProviderFixture() throws java.io.IOException {
      return new ScreenshotProviderFixture();
    }

    @Bean
    com.know.screenshots.application.ScreenshotStorage testScreenshotStorage(
        ScreenshotProviderFixture fixture) {
      return fixture.storage();
    }

    @Bean
    @org.springframework.context.annotation.Primary
    TestClock entityTestClock() {
      return new TestClock();
    }

    @Bean
    Filter fixtureAuthentication() {
      return (request, response, chain) -> {
        var context = SecurityContextHolder.createEmptyContext();
        var account = ((HttpServletRequest) request).getHeader("X-Test-Account");
        if (account != null)
          context.setAuthentication(
              UsernamePasswordAuthenticationToken.authenticated(
                  UUID.fromString(account), null, List.of()));
        SecurityContextHolder.setContext(context);
        try {
          chain.doFilter(request, response);
        } finally {
          SecurityContextHolder.clearContext();
        }
      };
    }
  }
}
