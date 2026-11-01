package com.know.entities.infrastructure.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.know.entities.application.*;
import com.know.shared.application.RequestFailure;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class HttpTvProviderTest {
  @Test
  void mapsProviderIdsSanitizesSummariesAndKeepsUnknownDates() throws Exception {
    var requests = new AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          requests.incrementAndGet();
          String body =
              switch (exchange.getRequestURI().getPath()) {
                case "/search/shows" ->
                    "[{\"show\":{\"id\":1,\"name\":\"Same"
                        + " title\",\"premiered\":\"2001-01-01\"}},{\"show\":{\"id\":2,\"name\":\"Same"
                        + " title\",\"premiered\":\"2020-01-01\"}}]";
                case "/shows/1" ->
                    "{\"id\":1,\"name\":\"Same"
                        + " title\",\"summary\":\"<p>Story<script>bad()</script></p>\",\"genres\":[\"Drama\"],\"rating\":{\"average\":8.5},\"externals\":{\"imdb\":\"tt12345\"}}";
                case "/shows/1/seasons" -> "[{\"id\":100,\"number\":1}]";
                case "/shows/1/episodes" ->
                    "[{\"id\":1001,\"season\":1,\"number\":1,\"name\":\"Pilot\",\"type\":\"regular\",\"airstamp\":null,\"airdate\":null},{\"id\":1002,\"season\":1,\"number\":null,\"name\":\"Special\",\"type\":\"significant_special\"}]";
                default -> "{}";
              };
          var bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          try (var out = exchange.getResponseBody()) {
            out.write(bytes);
          }
          exchange.close();
        });
    server.start();
    try {
      var store = mock(TvStore.class);
      when(store.claimProvider(any(), any(), any())).thenReturn(true);
      var provider = provider(server, store);
      assertThat(provider.search("Same title"))
          .extracting(TvModels.Candidate::tvmazeId)
          .containsExactly(1L, 2L);
      var catalog = provider.catalog(1);
      assertThat(catalog.show().summary()).isEqualTo("Story");
      assertThat(catalog.show().tvmazeRating()).isEqualTo(8.5);
      assertThat(catalog.show().imdbUrl()).isEqualTo("https://www.imdb.com/title/tt12345/");
      assertThat(catalog.episodes()).hasSize(1);
      assertThat(catalog.episodes().getFirst().airAt()).isNull();
      assertThat(catalog.episodes().getFirst().seasonId()).isEqualTo(100);
      assertThat(requests.get()).isEqualTo(4);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void respectsSharedCooldownAndRetryAfterWithoutReturningFalseEmptyResults() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          exchange.getResponseHeaders().set("Retry-After", "45");
          exchange.sendResponseHeaders(429, -1);
          exchange.close();
        });
    server.start();
    try {
      var store = mock(TvStore.class);
      when(store.claimProvider(any(), any(), any())).thenReturn(true);
      assertThatThrownBy(() -> provider(server, store).search("query"))
          .isInstanceOf(RequestFailure.class);
      verify(store).releaseProvider(any(), eq(Instant.parse("2026-10-25T18:30:45Z")));
      when(store.claimProvider(any(), any(), any())).thenReturn(false);
      assertThatThrownBy(() -> provider(server, store).search("query"))
          .isInstanceOf(RequestFailure.class);
    } finally {
      server.stop(0);
    }
  }

  private HttpTvProvider provider(HttpServer server, TvStore store) {
    return new HttpTvProvider(
        new TvProperties(
            "http://127.0.0.1:" + server.getAddress().getPort(),
            2,
            300,
            8,
            10000,
            Duration.ofHours(24),
            Duration.ofHours(24),
            Duration.ofMinutes(15),
            Duration.ofDays(7),
            Duration.ofMinutes(2),
            Duration.ofSeconds(30)),
        store,
        JsonMapper.builder().build(),
        Clock.fixed(Instant.parse("2026-10-25T18:30:00Z"), ZoneOffset.UTC));
  }
}
