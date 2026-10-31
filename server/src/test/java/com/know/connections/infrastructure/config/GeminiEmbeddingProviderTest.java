package com.know.connections.infrastructure.config;

import static org.assertj.core.api.Assertions.*;

import com.know.connections.application.EmbeddingFailure;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import tools.jackson.databind.json.JsonMapper;

class GeminiEmbeddingProviderTest {
  @Test
  void sendsApprovedTaskAndDimensionsAndPropagatesQuotaWithoutExposingProviderBody()
      throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var body = new AtomicReference<String>();
    var path = new AtomicReference<String>();
    var response = new AtomicReference<>("{\"totalTokens\":7}");
    var code = new java.util.concurrent.atomic.AtomicInteger(200);
    server.createContext(
        "/",
        exchange -> {
          path.set(exchange.getRequestURI().getPath());
          body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          exchange.getResponseHeaders().add("Retry-After", "120");
          byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(code.get(), bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try {
      var values =
          Map.<String, Object>of(
              "know.connections.embedding.live-requests-enabled",
              "true",
              "know.connections.embedding.unbilled-project-confirmed",
              "true",
              "know.connections.embedding.gemini.api-key",
              "fixture-key",
              "know.connections.embedding.gemini.base-url",
              "http://127.0.0.1:" + server.getAddress().getPort());
      var properties =
          new Binder(new MapConfigurationPropertySource(values))
              .bindOrCreate("know.connections", ConnectionProperties.class);
      assertThat(properties.embedding().gemini().baseUrl())
          .isEqualTo("http://127.0.0.1:" + server.getAddress().getPort());
      Instant now = Instant.parse("2026-10-31T12:00:00Z");
      var provider =
          new GeminiEmbeddingProvider(
              properties, JsonMapper.builder().build(), Clock.fixed(now, ZoneOffset.UTC));
      try {
        assertThat(provider.countTokens("article text")).isEqualTo(7);
      } catch (EmbeddingFailure error) {
        throw new AssertionError(
            "Fixture response status=" + error.status() + ", received=" + body.get(), error);
      }
      assertThat(body.get()).contains("article text");
      assertThat(path.get()).isEqualTo("/v1beta/models/gemini-embedding-001:countTokens");
      response.set("{\"embedding\":{\"values\":[1,0]}}");
      assertThat(provider.embed("article text", 2)).containsExactly(1, 0);
      assertThat(body.get()).contains("SEMANTIC_SIMILARITY", "outputDimensionality");
      assertThat(path.get()).isEqualTo("/v1beta/models/gemini-embedding-001:embedContent");
      code.set(429);
      response.set("private-provider-error");
      assertThatThrownBy(() -> provider.embed("article text", 2))
          .isInstanceOfSatisfying(
              EmbeddingFailure.class,
              error -> {
                assertThat(error.status()).isEqualTo(429);
                assertThat(error.retryAfter()).isEqualTo(now.plusSeconds(120));
                assertThat(error.getMessage())
                    .doesNotContain("private-provider-error", "fixture-key");
              });
      code.set(200);
      response.set("{\"embedding\":{\"values\":[0]}}");
      assertThatThrownBy(() -> provider.embed("article text", 2))
          .isInstanceOf(EmbeddingFailure.class);
    } finally {
      server.stop(0);
    }
  }
}
