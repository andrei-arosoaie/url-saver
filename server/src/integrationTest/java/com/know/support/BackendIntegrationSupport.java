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
  // JVM-scoped rather than JUnit class-scoped: a cached context must keep its database alive.
  // Testcontainers/Ryuk cleans up this disposable container when the test JVM exits.
  private static final PostgreSQLContainer POSTGRES = startDatabase();
  private static final AtomicReference<ApplicationContext> SHARED_CONTEXT = new AtomicReference<>();

  private static PostgreSQLContainer startDatabase() {
    var database = new PostgreSQLContainer("postgres:17.11-alpine");
    database.start();
    return database;
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
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
