package com.know.entities.presentation;

import static org.assertj.core.api.Assertions.*;

import com.know.entities.application.*;
import com.know.support.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor(onConstructor_ = @Autowired)
class TvFlowsIT extends BackendIntegrationSupport {
  private final JdbcTemplate jdbc;
  private final Environment environment;
  private final TvStore store;
  private final TvProviderFixture provider;
  private final TvPolicy policy;
  private final HttpClient http = HttpClient.newHttpClient();
  private final JsonMapper json = JsonMapper.builder().build();
  private UUID owner, other;
  private static final Instant NOW = Instant.parse("2026-10-25T18:30:00Z");

  @BeforeEach
  void setup() {
    jdbc.execute(
        "truncate"
            + " entity_episode_watches,entity_tv_shows,tvmaze_refresh_jobs,tvmaze_episodes,tvmaze_seasons,tvmaze_shows,tvmaze_search_cache,embedding_provider_permits,embedding_provider_cooldowns,connection_shared_entities,article_connections,article_connection_exclusions,article_embeddings,article_connection_jobs,identification_jobs,identification_provider_health,screenshot_cleanup,article_screenshots,entity_completions,article_entities,entities,articles,categories,accounts");
    owner = UUID.randomUUID();
    other = UUID.randomUUID();
    for (var id : List.of(owner, other))
      jdbc.update("insert into accounts(id,created_at) values (?,now())", id);
    entityTestClock.set(NOW);
    provider.unavailable = false;
    provider.searches.set(0);
  }

  @Test
  void identityProgressHistoryReplacementAndOwnership() throws Exception {
    var created = request(201, "POST", "/tvmaze/entities", owner, Map.of("tvmazeId", 1));
    String id = created.path("id").asString();
    String path = "/entities/" + id + "/tv-show";
    categoryFixtures.ensureDefaults(owner);
    var article =
        request(
                201,
                "POST",
                "/articles",
                owner,
                Map.of("url", "https://example.test/tv", "title", "Source page"))
            .path("article")
            .path("id")
            .asString();
    request(201, "PUT", "/articles/" + article + "/entities/" + id, owner, null);
    request(201, "POST", "/tvmaze/entities", owner, Map.of("tvmazeId", 2));
    request(409, "POST", "/tvmaze/entities", owner, Map.of("tvmazeId", 1));
    request(201, "POST", "/tvmaze/entities", other, Map.of("tvmazeId", 1));
    request(404, "GET", path, other, null);
    request(404, "GET", path + "/seasons", other, null);
    var locked =
        request(400, "PATCH", "/entities/" + id, owner, Map.of("version", 0, "title", "Rename"));
    assertThat(locked.path("reason").asString()).isEqualTo("IDENTIFIED_TITLE_READ_ONLY");
    publish(1);
    publish(2);
    var d = request(200, "GET", path, owner, null);
    assertThat(d.path("airedCount").asLong()).isEqualTo(2);
    assertThat(d.path("canMarkSeen").asBoolean()).isFalse();
    request(409, "POST", path + "/mark-seen", owner, versions(d));
    request(404, "GET", path + "/seasons/200/episodes", owner, null);
    request(
        409,
        "PUT",
        path + "/episodes/1004/watched",
        owner,
        Map.of("version", d.path("version").asLong(), "watched", true));
    var selected =
        request(
            200,
            "PATCH",
            path + "/position",
            owner,
            Map.of(
                "version",
                d.path("version").asLong(),
                "episodeId",
                1002,
                "markPreviousWatched",
                false));
    assertThat(selected.path("status").asString()).isEqualTo("SEEING");
    assertThat(selected.path("watchedCount").asLong()).isZero();
    var stale =
        request(
            409,
            "PATCH",
            path,
            owner,
            Map.of("version", d.path("version").asLong(), "alias", "Label"));
    assertThat(stale.path("reason").asString()).isEqualTo("VERSION_CONFLICT");
    d =
        request(
            200,
            "PATCH",
            path + "/position",
            owner,
            Map.of(
                "version",
                selected.path("version").asLong(),
                "episodeId",
                1002,
                "markPreviousWatched",
                true));
    assertThat(d.path("watchedCount").asLong()).isEqualTo(2);
    d =
        request(
            200,
            "PUT",
            path + "/episodes/1003/watched",
            owner,
            Map.of("version", d.path("version").asLong(), "watched", true));
    assertThat(d.path("canMarkSeen").asBoolean()).isTrue();
    d = request(200, "POST", path + "/mark-seen", owner, versions(d));
    assertThat(d.path("status").asString()).isEqualTo("SEEN");
    var seenVersion = d.path("version").asLong();
    d = request(200, "POST", path + "/mark-seen", owner, versions(d));
    assertThat(d.path("version").asLong()).isEqualTo(seenVersion);
    var history = request(200, "GET", "/entities/" + id + "/completions", owner, null);
    assertThat(history.path("items").size()).isEqualTo(1);
    d =
        request(
            200,
            "PUT",
            path + "/episodes/1001/watched",
            owner,
            Map.of("version", d.path("version").asLong(), "watched", false));
    assertThat(d.path("status").asString()).isEqualTo("SEEN");
    assertThat(d.path("lastWatchedEpisodeId").asLong()).isEqualTo(1002);
    d =
        request(
            200,
            "PATCH",
            path,
            owner,
            Map.of("version", d.path("version").asLong(), "alias", "My alias"));
    assertThat(
            request(200, "GET", "/entities?type=TV_SHOW&query=alias", owner, null)
                .path("items")
                .size())
        .isEqualTo(1);
    var replacement = new HashMap<String, Object>(versions(d));
    replacement.put("tvmazeId", 3);
    replacement.put("confirmResetProgress", false);
    request(400, "POST", path + "/change-show", owner, replacement);
    assertThat(request(200, "GET", path, owner, null).path("tvmazeId").asLong()).isEqualTo(1);
    replacement.put("confirmResetProgress", true);
    replacement.put("tvmazeId", 2);
    request(409, "POST", path + "/change-show", owner, replacement);
    assertThat(request(200, "GET", path, owner, null).path("watchedCount").asLong())
        .isEqualTo(d.path("watchedCount").asLong());
    replacement.put("tvmazeId", 3);
    d = request(200, "POST", path + "/change-show", owner, replacement);
    assertThat(d.path("tvmazeId").asLong()).isEqualTo(3);
    assertThat(d.path("alias").asString()).isEqualTo("My alias");
    assertThat(d.path("status").asString()).isEqualTo("TO_BE_SEEN");
    assertThat(d.path("lastWatchedEpisodeId").isNull()).isTrue();
    assertThat(d.path("watchedCount").asLong()).isZero();
    assertThat(
            request(200, "GET", "/entities/" + id + "/articles", owner, null)
                .path("items")
                .get(0)
                .path("id")
                .asString())
        .isEqualTo(article);
    assertThat(
            request(200, "GET", "/entities/" + id + "/completions", owner, null)
                .path("items")
                .size())
        .isEqualTo(1);
  }

  @Test
  void newlyAiredEpisodesDowngradeAndClearOnlyAfterWatching() throws Exception {
    var e = request(201, "POST", "/tvmaze/entities", owner, Map.of("tvmazeId", 1));
    publish(1);
    String id = e.path("id").asString(), path = "/entities/" + id + "/tv-show";
    var d = request(200, "GET", path, owner, null);
    d =
        request(
            200,
            "PATCH",
            path + "/position",
            owner,
            Map.of(
                "version",
                d.path("version").asLong(),
                "episodeId",
                1002,
                "markPreviousWatched",
                true));
    d = request(200, "POST", path + "/mark-seen", owner, versions(d));
    entityTestClock.set(Instant.parse("2026-10-26T00:01:00Z"));
    store.evaluateAirings(entityTestClock.instant());
    d = request(200, "GET", path, owner, null);
    assertThat(d.path("status").asString()).isEqualTo("TO_BE_SEEN");
    assertThat(d.path("newEpisodesAvailable").asBoolean()).isTrue();
    var list = request(200, "GET", "/entities?type=TV_SHOW", owner, null).path("items").get(0);
    assertThat(list.path("tvShow").path("newEpisodesAvailable").asBoolean()).isTrue();
    assertThat(list.path("tvShow").path("position").asString()).isEqualTo("S01E02");
    d =
        request(
            200,
            "PATCH",
            path + "/position",
            owner,
            Map.of("version", d.path("version").asLong(), "episodeId", 1004));
    assertThat(d.path("newEpisodesAvailable").asBoolean()).isTrue();
    d =
        request(
            200,
            "PUT",
            path + "/episodes/1004/watched",
            owner,
            Map.of("version", d.path("version").asLong(), "watched", true));
    assertThat(d.path("newEpisodesAvailable").asBoolean()).isFalse();
    assertThat(d.path("status").asString()).isEqualTo("SEEING");
    assertThat(
            request(200, "GET", "/entities/" + id + "/completions", owner, null)
                .path("items")
                .size())
        .isEqualTo(1);
  }

  @Test
  void sharedSearchCacheSurvivesServiceInstancesNormalizesAndRetainsAllCandidates()
      throws Exception {
    var first = request(200, "GET", "/tvmaze/shows?query=Same%20%20Title", owner, null);
    assertThat(first.path("items").size()).isEqualTo(12);
    request(200, "GET", "/tvmaze/shows?query=same%20title", other, null);
    assertThat(provider.searches.get()).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from tvmaze_search_cache", Integer.class))
        .isEqualTo(1);
    var another =
        new TvSearch(
            () -> owner,
            store,
            provider,
            new com.know.entities.infrastructure.config.TvProperties(
                "unused",
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
            entityTestClock);
    assertThat(another.search(" SAME title ").items()).hasSize(12);
    assertThat(provider.searches.get()).isEqualTo(1);
    entityTestClock.set(NOW.plus(Duration.ofHours(25)));
    provider.unavailable = true;
    var stale = request(200, "GET", "/tvmaze/shows?query=same%20title", owner, null);
    assertThat(stale.path("state").asString()).isEqualTo("STALE_UNAVAILABLE");
    request(503, "GET", "/tvmaze/shows?query=other", owner, null);
    assertThat(store.cached("tvmaze:shows:v1:other")).isEmpty();
    provider.unavailable = false;
    request(200, "GET", "/tvmaze/shows?query=empty", owner, null);
    int calls = provider.searches.get();
    entityTestClock.set(entityTestClock.instant().plus(Duration.ofMinutes(16)));
    request(200, "GET", "/tvmaze/shows?query=empty", owner, null);
    assertThat(provider.searches.get()).isEqualTo(calls + 1);
    var token = UUID.randomUUID();
    var now = entityTestClock.instant();
    assertThat(store.claimSearch("lease", "lease", token, now, now.plusSeconds(10))).isTrue();
    assertThat(store.claimSearch("lease", "lease", UUID.randomUUID(), now, now.plusSeconds(10)))
        .isFalse();
    assertThat(
            store.claimSearch(
                "lease", "lease", UUID.randomUUID(), now.plusSeconds(11), now.plusSeconds(30)))
        .isTrue();
  }

  @Test
  void seeingIsTvOnlyAndManualTitleIdentityIsPreserved() throws Exception {
    request(
        400,
        "POST",
        "/entities",
        owner,
        Map.of("type", "MOVIE", "title", "Movie", "status", "SEEING"));
    var e =
        request(
            201,
            "POST",
            "/entities",
            owner,
            Map.of("type", "TV_SHOW", "title", "Manual", "status", "SEEING"));
    request(409, "POST", "/entities", owner, Map.of("type", "TV_SHOW", "title", "Manual"));
    assertThat(e.path("totalCompletionCount").asLong()).isZero();
    request(
        200,
        "PATCH",
        "/entities/" + e.path("id").asString(),
        owner,
        Map.of("version", 0, "title", "Edited"));
  }

  @Test
  void refreshWorkerLoadsQueuedCatalogAndMakesSeasonsAndEpisodesSelectable() throws Exception {
    var created = request(201, "POST", "/tvmaze/entities", owner, Map.of("tvmazeId", 1));
    var id = created.path("id").asString();
    var path = "/entities/" + id + "/tv-show";
    assertThat(request(200, "GET", path, owner, null).path("metadata").path("state").asString())
        .isEqualTo("PENDING");
    var worker =
        new com.know.entities.infrastructure.config.TvWorker(
            store, provider, policy, entityTestClock);
    worker.poll();
    var loaded = request(200, "GET", path, owner, null);
    assertThat(loaded.path("metadata").path("state").asString()).isEqualTo("READY");
    var seasons = request(200, "GET", path + "/seasons", owner, null).path("items");
    assertThat(seasons.size()).isEqualTo(1);
    var episodes =
        request(
                200,
                "GET",
                path + "/seasons/" + seasons.get(0).path("id").asLong() + "/episodes",
                owner,
                null)
            .path("items");
    assertThat(episodes.size()).isEqualTo(4);
    assertThat(episodes.get(0).path("canMarkWatched").asBoolean()).isTrue();
    request(
        200,
        "PATCH",
        path + "/position",
        owner,
        Map.of(
            "version",
            loaded.path("version").asLong(),
            "episodeId",
            episodes.get(0).path("id").asLong()));
    var fetched = store.metadata(1).fetchedAt();
    worker.poll();
    assertThat(store.metadata(1).fetchedAt()).isEqualTo(fetched);
    entityTestClock.set(NOW.plus(policy.catalogTtl()).plusSeconds(1));
    worker.poll();
    assertThat(store.metadata(1).fetchedAt()).isEqualTo(entityTestClock.instant());
  }

  @Test
  void concurrentSearchesCoalesceAndExpiredCatalogWorkersCannotPublish() throws Exception {
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var start = new CountDownLatch(1);
      var a =
          pool.submit(
              () -> {
                start.await();
                return request(200, "GET", "/tvmaze/shows?query=concurrent", owner, null);
              });
      var b =
          pool.submit(
              () -> {
                start.await();
                return request(200, "GET", "/tvmaze/shows?query=concurrent", other, null);
              });
      start.countDown();
      a.get(15, TimeUnit.SECONDS);
      b.get(15, TimeUnit.SECONDS);
      assertThat(provider.searches.get()).isEqualTo(1);
    }
    request(201, "POST", "/tvmaze/entities", owner, Map.of("tvmazeId", 1));
    var expired = store.claim(NOW, NOW.plusSeconds(10)).orElseThrow();
    assertThat(store.claim(NOW, NOW.plusSeconds(10))).isEmpty();
    var recovered = store.claim(NOW.plusSeconds(11), NOW.plusSeconds(120)).orElseThrow();
    store.publish(expired, provider.catalog(1), NOW.plusSeconds(11));
    assertThat(store.metadata(1).fetchedAt()).isNull();
    store.publish(recovered, provider.catalog(1), NOW.plusSeconds(11));
    assertThat(store.metadata(1).state()).isEqualTo("READY");
    assertThat(jdbc.queryForObject("select count(*) from tvmaze_episodes", Integer.class))
        .isEqualTo(4);
  }

  private void publish(long id) {
    // Claim exactly this show's job; worker and test share the same durable publication fences.
    jdbc.update(
        "update tvmaze_refresh_jobs set available_at=? where show_id=?",
        java.sql.Timestamp.from(NOW.minusSeconds(1)),
        id);
    var lease = store.claim(NOW, NOW.plusSeconds(120)).orElseThrow();
    store.publish(lease, provider.catalog(lease.showId()), NOW);
  }

  private Map<String, Object> versions(JsonNode d) {
    return Map.of(
        "version", d.path("version").asLong(), "entityVersion", d.path("entityVersion").asLong());
  }

  private JsonNode request(int status, String method, String path, UUID account, Object body)
      throws Exception {
    var builder =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:"
                        + environment.getProperty("local.server.port")
                        + "/api/v1"
                        + path))
            .header("X-Test-Account", account.toString());
    if (body != null) builder.header("Content-Type", "application/json");
    var response =
        http.send(
            builder
                .method(
                    method,
                    body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
    return json.readTree(response.body());
  }
}
