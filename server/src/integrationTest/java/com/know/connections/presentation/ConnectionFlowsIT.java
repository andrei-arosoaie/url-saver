package com.know.connections.presentation;

import static org.assertj.core.api.Assertions.*;

import com.know.connections.application.*;
import com.know.connections.infrastructure.config.ConnectionWorker;
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
class ConnectionFlowsIT extends BackendIntegrationSupport {
  private final JdbcTemplate jdbc;
  private final Environment environment;
  private final ConnectionWorker worker;
  private final ConnectionStore store;
  private final ConnectionProfiles profiles;
  private final ConnectionPolicy policy;
  private final com.know.connections.infrastructure.config.ConnectionProperties properties;
  private final EmbeddingProviderFixture provider;
  private final HttpClient http = HttpClient.newHttpClient();
  private final JsonMapper json = JsonMapper.builder().build();
  private UUID owner, other;
  private static final Instant NOW = Instant.parse("2026-10-25T18:30:00Z");

  @BeforeEach
  void fixtures() {
    jdbc.execute(
        "truncate identity_refresh_tokens,identity_codes,identity_sessions,external_account_providers,entity_episode_watches,entity_tv_shows,tvmaze_refresh_jobs,tvmaze_episodes,tvmaze_seasons,tvmaze_shows,tvmaze_search_cache,embedding_provider_permits,embedding_provider_cooldowns,connection_shared_entities,article_connections,article_connection_exclusions,article_embeddings,article_connection_jobs,identification_jobs,identification_provider_health,screenshot_cleanup,article_screenshots,entity_completions,article_entities,entities,articles,categories,accounts");
    jdbc.update("update embedding_profiles set backfill_after=null,backfill_complete=false");
    owner = UUID.randomUUID();
    other = UUID.randomUUID();
    for (UUID id : List.of(owner, other))
      jdbc.update("insert into accounts(id,created_at) values (?,now())", id);
    entityTestClock.set(NOW);
    provider.reset();
  }

  @Test
  void saveEmbedMergeReasonsReadBothDirectionsRemoveAndNeverRecreate() throws Exception {
    UUID a = save(owner, "a", "<p>Same subject from the first article.</p>");
    UUID b = save(owner, "b", "<p>Same subject discussed by the second article.</p>");
    UUID foreign = save(other, "private", "<p>Same subject.</p>");
    request(
        201,
        "POST",
        "/articles/" + a + "/entities",
        owner,
        Map.of("type", "BOOK", "title", "Dune"));
    request(
        200,
        "POST",
        "/articles/" + b + "/entities",
        owner,
        Map.of("type", "BOOK", "title", "Dune"));
    drain();
    var page = connections(a, owner);
    assertThat(page.path("items").size()).isEqualTo(1);
    assertThat(page.path("items").get(0).path("id").asText()).isEqualTo(b.toString());
    assertThat(page.path("items").get(0).path("sharedEntities").get(0).path("name").asText())
        .isEqualTo("Dune");
    assertThat(page.path("items").get(0).path("semanticScore").asDouble()).isEqualTo(1);
    assertThat(connections(b, owner).path("items").get(0).path("id").asText())
        .isEqualTo(a.toString());
    request(404, "GET", "/articles/" + foreign + "/connections", owner, null);
    request(404, "DELETE", "/articles/" + a + "/connections/" + foreign, owner, null);
    request(204, "DELETE", "/articles/" + a + "/connections/" + b, owner, null);
    request(204, "DELETE", "/articles/" + b + "/connections/" + a, owner, null);
    assertThat(connections(b, owner).path("items").isEmpty()).isTrue();
    request(
        200,
        "PATCH",
        "/articles/" + a,
        owner,
        Map.of("version", 0, "content", "<p>Same subject, updated.</p>"));
    drain();
    store.backfill(profiles.current(), 50, entityTestClock.instant());
    drain();
    assertThat(connections(a, owner).path("items").isEmpty()).isTrue();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from article_connection_exclusions", Integer.class))
        .isEqualTo(1);
    request(204, "DELETE", "/articles/" + a + "?version=1", owner, null);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from article_connection_exclusions", Integer.class))
        .isZero();
    request(200, "GET", "/articles/" + b, owner, null);
  }

  @Test
  void quotaPauseSurvivesWorkerRestartAndDoesNotConsumeRetries() throws Exception {
    UUID a = save(owner, "quota", "<p>Article subject.</p>");
    provider.status = 429;
    provider.failures.set(1);
    drain();
    var page = connections(a, owner);
    assertThat(page.path("processing").path("state").asText()).isEqualTo("PAUSED");
    assertThat(
            jdbc.queryForObject(
                "select attempt_count from article_connection_jobs where article_id=? and kind='EMBEDDING'",
                Integer.class,
                a))
        .isZero();
    assertThat(worker.runOne()).isFalse();
    entityTestClock.set(NOW.plus(Duration.ofHours(24)).plusSeconds(1));
    var restarted = new ConnectionWorker(store, provider, profiles, properties, entityTestClock);
    try {
      for (int i = 0; i < 20 && restarted.runOne(); i++) {}
    } finally {
      restarted.close();
    }
    assertThat(connections(a, owner).path("processing").path("state").asText())
        .isEqualTo("COMPLETE");
  }

  @Test
  void longTextCoversTheTailReusesVectorsAndRejectsStaleInFlightWork() throws Exception {
    String text = "Article subject ".repeat(1800) + "TAIL_MARKER";
    UUID a = save(owner, "long", "<p>" + text + "</p>");
    for (int n = 0; n < 30; n++) {
      drain();
      entityTestClock.set(entityTestClock.instant().plusSeconds(3));
    }
    assertThat(provider.embedded).anyMatch(value -> value.endsWith("TAIL_MARKER"));
    int calls = provider.embedded.size();
    request(
        201,
        "POST",
        "/articles/" + a + "/entities",
        owner,
        Map.of("type", "BOOK", "title", "Book"));
    drain();
    assertThat(provider.embedded).hasSize(calls);
    UUID b = save(owner, "other", "<p>Article subject.</p>");
    drain();
    var latch = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    provider.beforeEmbedding =
        () -> {
          latch.countDown();
          try {
            release.await(10, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        };
    request(
        200,
        "PATCH",
        "/articles/" + a,
        owner,
        Map.of("version", 0, "content", "<p>old pending revision</p>"));
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var running =
          executor.submit(
              () -> {
                for (int n = 0; n < 3; n++) worker.runOne();
              });
      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
      request(
          200,
          "PATCH",
          "/articles/" + a,
          owner,
          Map.of("version", 1, "content", "<p>unrelated newest revision</p>"));
      release.countDown();
      running.get(15, TimeUnit.SECONDS);
    } finally {
      release.countDown();
      provider.beforeEmbedding = () -> {};
    }
    entityTestClock.set(entityTestClock.instant().plusSeconds(3));
    drain();
    assertThat(connections(a, owner).path("items").isEmpty()).isTrue();
    assertThat(connections(b, owner).path("items").isEmpty()).isTrue();
  }

  @Test
  void databasePermitsAreGlobalAndExpiredJobClaimsAreFenced() throws Exception {
    UUID a = save(owner, "permit-a", "<p>Article subject.</p>");
    save(owner, "permit-b", "<p>Article subject.</p>");
    var first = store.claim(NOW, policy.leaseDuration());
    var second = store.claim(NOW, policy.leaseDuration());
    var permit = store.acquire(first, true, policy, NOW);
    assertThat(permit.acquired()).isTrue();
    assertThat(store.acquire(second, true, policy, NOW).acquired()).isFalse();
    store.release(permit);
    assertThat(store.acquire(second, true, policy, NOW).acquired()).isTrue();
    var later = NOW.plus(policy.leaseDuration()).plusSeconds(1);
    assertThat(store.renew(first, null, later, policy.leaseDuration())).isFalse();
    assertThat(store.current(first, later)).isFalse();
    assertThat(store.claim(later, policy.leaseDuration())).isNotNull();
    request(404, "GET", "/articles/" + a + "/connections", other, null);
  }

  private void drain() {
    for (int i = 0; i < 200 && worker.runOne(); i++) {}
  }

  @Test
  void
      pausedConfigurationSendsNoRequestsAndEnablingItRecoversLegacyJobsWithoutResettingQuotaDelays()
          throws Exception {
    UUID a = save(owner, "configuration-a", "<p>Article subject.</p>");
    UUID b = save(owner, "configuration-b", "<p>Article subject.</p>");
    var pausedProperties =
        new org.springframework.boot.context.properties.bind.Binder(
                new org.springframework.boot.context.properties.source
                    .MapConfigurationPropertySource(
                    Map.of(
                        "know.connections.enabled",
                        "true",
                        "know.connections.scheduling-enabled",
                        "false",
                        "know.connections.embedding.dimensions",
                        "128",
                        "know.connections.embedding.live-requests-enabled",
                        "false")))
            .bindOrCreate(
                "know.connections",
                com.know.connections.infrastructure.config.ConnectionProperties.class);
    var requests = new java.util.concurrent.atomic.AtomicInteger();
    provider.beforeCounting = requests::incrementAndGet;
    var paused = new ConnectionWorker(store, provider, profiles, pausedProperties, entityTestClock);
    try {
      for (int i = 0; i < 10 && paused.runOne(); i++) {}
    } finally {
      paused.close();
    }
    assertThat(requests.get()).isZero();
    assertThat(provider.embedded).isEmpty();
    var state = store.progress(owner, a, profiles.current(), pausedProperties.policy(), NOW);
    assertThat(state.state()).isEqualTo("PAUSED");
    assertThat(state.reason()).isEqualTo("LIVE_REQUESTS_DISABLED");
    assertThat(state.nextAttemptAt()).isNull();
    // Reproduce the old production configuration pause and a separate quota pause.
    jdbc.update(
        "update article_connection_jobs set available_at=? where article_id=? and kind='EMBEDDING'",
        java.sql.Timestamp.from(NOW.plusSeconds(86400)),
        a);
    jdbc.update(
        "update article_connection_jobs set available_at=?,last_error_code='PROVIDER_QUOTA' where article_id=? and kind='EMBEDDING'",
        java.sql.Timestamp.from(NOW.plusSeconds(86400)),
        b);
    worker.ready();
    assertThat(
            jdbc.queryForObject(
                    "select available_at from article_connection_jobs where article_id=? and kind='EMBEDDING'",
                    java.sql.Timestamp.class,
                    a)
                .toInstant())
        .isEqualTo(NOW);
    assertThat(
            jdbc.queryForObject(
                    "select available_at from article_connection_jobs where article_id=? and kind='EMBEDDING'",
                    java.sql.Timestamp.class,
                    b)
                .toInstant())
        .isEqualTo(NOW.plusSeconds(86400));
    drain();
    assertThat(connections(a, owner).path("processing").path("state").asText())
        .isEqualTo("COMPLETE");
    entityTestClock.set(NOW.plusSeconds(86401));
    drain();
    assertThat(connections(a, owner).path("items").size()).isEqualTo(1);
    assertThat(connections(b, owner).path("items").size()).isEqualTo(1);
  }

  @Test
  void revisionChangedBetweenCountingAndEmbeddingReleasesClaimWithoutWaitingForLeaseExpiry()
      throws Exception {
    UUID id = save(owner, "changed-after-counting", "<p>Original text.</p>");
    assertThat(worker.runOne()).isTrue();
    provider.beforeCounting =
        () -> {
          provider.beforeCounting = () -> {};
          try {
            request(
                200,
                "PATCH",
                "/articles/" + id,
                owner,
                Map.of("version", 0, "content", "<p>Latest captured text.</p>"));
          } catch (Exception error) {
            throw new RuntimeException(error);
          }
        };
    assertThat(worker.runOne()).isTrue();
    assertThat(
            jdbc.queryForObject(
                "select status from article_connection_jobs where article_id=? and kind='EMBEDDING'",
                String.class,
                id))
        .isEqualTo("PENDING");
    entityTestClock.set(NOW.plusSeconds(3));
    drain();
    assertThat(connections(id, owner).path("processing").path("state").asText())
        .isEqualTo("COMPLETE");
    assertThat(provider.embedded).containsExactly("Latest captured text.");
  }

  @Test
  void deletingArticleDuringEmbeddingCannotResurrectJobsVectorsOrConnections() throws Exception {
    UUID a = save(owner, "delete-a", "<p>Article subject.</p>");
    UUID b = save(owner, "delete-b", "<p>Article subject.</p>");
    drain();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    provider.beforeEmbedding =
        () -> {
          entered.countDown();
          try {
            release.await(10, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        };
    request(
        200,
        "PATCH",
        "/articles/" + a,
        owner,
        Map.of("version", 0, "content", "<p>Revised article subject.</p>"));
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var running =
          executor.submit(
              () -> {
                worker.runOne();
                worker.runOne();
              });
      assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
      request(204, "DELETE", "/articles/" + a + "?version=1", owner, null);
      release.countDown();
      running.get(15, TimeUnit.SECONDS);
    } finally {
      release.countDown();
      provider.beforeEmbedding = () -> {};
    }
    drain();
    request(404, "GET", "/articles/" + a, owner, null);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from article_connection_jobs where article_id=?",
                Integer.class,
                a))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from article_embeddings where article_id=?", Integer.class, a))
        .isZero();
    assertThat(connections(b, owner).path("items").isEmpty()).isTrue();
  }

  @Test
  void twoWorkerInstancesShareOneProviderSlotAndRemovalWinsAgainstInFlightGeneration()
      throws Exception {
    UUID a = save(owner, "parallel-a", "<p>Article subject.</p>");
    UUID b = save(owner, "parallel-b", "<p>Article subject.</p>");
    // Publish shared evidence first; both following jobs perform embeddings.
    assertThat(worker.runOne()).isTrue();
    assertThat(worker.runOne()).isTrue();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var inside = new java.util.concurrent.atomic.AtomicInteger();
    var maximum = new java.util.concurrent.atomic.AtomicInteger();
    provider.beforeEmbedding =
        () -> {
          int count = inside.incrementAndGet();
          maximum.accumulateAndGet(count, Math::max);
          entered.countDown();
          try {
            release.await(10, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            inside.decrementAndGet();
          }
        };
    var second = new ConnectionWorker(store, provider, profiles, properties, entityTestClock);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var running = executor.submit(worker::runOne);
      assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(second.runOne()).isTrue();
      request(204, "DELETE", "/articles/" + a + "/connections/" + b, owner, null);
      release.countDown();
      assertThat(running.get(15, TimeUnit.SECONDS)).isTrue();
    } finally {
      release.countDown();
      provider.beforeEmbedding = () -> {};
      second.close();
    }
    entityTestClock.set(NOW.plusSeconds(3));
    drain();
    assertThat(maximum.get()).isEqualTo(1);
    assertThat(provider.embedded).hasSize(2);
    assertThat(connections(a, owner).path("items").isEmpty()).isTrue();
    assertThat(connections(b, owner).path("items").isEmpty()).isTrue();
  }

  @Test
  void embeddingCompletionDuringMatchingRetainsItsFollowUpAndBackfillSkipsLinkOnly()
      throws Exception {
    UUID a = save(owner, "queued-a", "<p>Article subject.</p>");
    save(owner, "queued-b", "<p>Article subject.</p>");
    var matching = store.claim(NOW, policy.leaseDuration());
    assertThat(matching.kind()).isEqualTo(ConnectionModels.Kind.CONNECTIONS);
    drain();
    assertThat(
            jdbc.queryForObject(
                "select rerun_requested from article_connection_jobs where id=?",
                Boolean.class,
                matching.id()))
        .isTrue();
    store.finish(matching, null, NOW);
    assertThat(
            jdbc.queryForObject(
                "select status from article_connection_jobs where id=?",
                String.class,
                matching.id()))
        .isEqualTo("PENDING");
    drain();
    assertThat(connections(a, owner).path("items").size()).isEqualTo(1);
    var link =
        request(
            201,
            "POST",
            "/articles",
            owner,
            Map.of("url", "https://example.com/link-only", "title", "Link"));
    UUID linkId = UUID.fromString(link.path("article").path("id").asText());
    request(
        201,
        "POST",
        "/articles/" + a + "/entities",
        owner,
        Map.of("type", "BOOK", "title", "Shared book"));
    request(
        200,
        "POST",
        "/articles/" + linkId + "/entities",
        owner,
        Map.of("type", "BOOK", "title", "Shared book"));
    store.backfill(profiles.current(), 50, NOW);
    drain();
    assertThat(connections(linkId, owner).path("processing").path("state").asText())
        .isEqualTo("SKIPPED");
    assertThat(connections(linkId, owner).path("items").isEmpty()).isTrue();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from article_connection_jobs where article_id=?",
                Integer.class,
                linkId))
        .isZero();
    int jobs = jdbc.queryForObject("select count(*) from article_connection_jobs", Integer.class);
    request(
        400,
        "POST",
        "/articles",
        owner,
        Map.of("url", "https://example.com/invalid", "title", " ", "content", "<p>Text</p>"));
    assertThat(jdbc.queryForObject("select count(*) from article_connection_jobs", Integer.class))
        .isEqualTo(jobs);
  }

  @Test
  void paginatesAllConnectionsScopesBookmarksAndDoesNotReembedMetadataEdits() throws Exception {
    UUID source = save(owner, "source", "<p>Article subject.</p>");
    for (int i = 0; i < 13; i++) save(owner, "candidate-" + i, "<p>Article subject.</p>");
    drain();
    Set<String> ids = new HashSet<>();
    String cursor = null;
    do {
      var page =
          request(
              200,
              "GET",
              "/articles/"
                  + source
                  + "/connections?limit=5"
                  + (cursor == null ? "" : "&cursor=" + cursor),
              owner,
              null);
      for (var row : page.path("items")) assertThat(ids.add(row.path("id").asText())).isTrue();
      cursor = page.path("nextCursor").isNull() ? null : page.path("nextCursor").asText();
      if (cursor != null)
        request(
            400,
            "GET",
            "/articles/" + ids.iterator().next() + "/connections?cursor=" + cursor,
            owner,
            null);
    } while (cursor != null);
    assertThat(ids).hasSize(13);
    int calls = provider.embedded.size();
    request(
        200,
        "PATCH",
        "/articles/" + source,
        owner,
        Map.of("version", 0, "title", "Renamed", "entityName", "Manual name"));
    drain();
    assertThat(provider.embedded).hasSize(calls);
    assertThat(connections(source, owner).path("items").size()).isEqualTo(13);
  }

  @Test
  void preservesConnectionsDuringFailureAndManualRetryRecoversAfterConfiguredBudget()
      throws Exception {
    UUID a = save(owner, "retry-a", "<p>Article subject.</p>");
    UUID b = save(owner, "retry-b", "<p>Article subject.</p>");
    drain();
    provider.failures.set(6);
    request(
        200,
        "PATCH",
        "/articles/" + a,
        owner,
        Map.of("version", 0, "content", "<p>Article subject revised.</p>"));
    drain();
    for (Duration delay : policy.retryDelays()) {
      entityTestClock.set(entityTestClock.instant().plus(delay).plusSeconds(1));
      drain();
    }
    assertThat(connections(a, owner).path("processing").path("state").asText()).isEqualTo("FAILED");
    assertThat(connections(a, owner).path("items").get(0).path("id").asText())
        .isEqualTo(b.toString());
    assertThat(connections(a, owner).path("items").get(0).path("semanticUpdating").asBoolean())
        .isTrue();
    request(202, "POST", "/articles/" + a + "/connections/retry", owner, null);
    drain();
    assertThat(connections(a, owner).path("processing").path("state").asText())
        .isEqualTo("COMPLETE");
    assertThat(connections(a, owner).path("items").get(0).path("semanticUpdating").asBoolean())
        .isFalse();
  }

  private UUID save(UUID account, String name, String content) throws Exception {
    return UUID.fromString(
        request(
                201,
                "POST",
                "/articles",
                account,
                Map.of("url", "https://example.com/" + name, "title", name, "content", content))
            .path("article")
            .path("id")
            .asText());
  }

  private JsonNode connections(UUID article, UUID account) throws Exception {
    return request(200, "GET", "/articles/" + article + "/connections", account, null);
  }

  private JsonNode request(int status, String method, String path, UUID account, Object body)
      throws Exception {
    var request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:"
                        + environment.getProperty("local.server.port")
                        + "/api/v1"
                        + path))
            .header("X-Test-Account", account.toString())
            .header("Content-Type", "application/json")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
    return response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
  }
}
