package com.know.articles.presentation;

import static org.assertj.core.api.Assertions.*;

import com.know.articles.infrastructure.config.ArticleProperties;
import com.know.support.BackendIntegrationSupport;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
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
class ArticleFlowsIT extends BackendIntegrationSupport {
  private final JdbcTemplate jdbc;
  private final Environment environment;
  private final ArticleProperties properties;
  private final JsonMapper json = JsonMapper.builder().build();
  private final HttpClient client = HttpClient.newHttpClient();
  private UUID owner;
  private UUID other;

  @BeforeEach
  void fixtures() {
    jdbc.execute(
        "truncate identity_refresh_tokens,identity_codes,identity_sessions,external_account_providers,entity_episode_watches,entity_tv_shows,tvmaze_refresh_jobs,tvmaze_episodes,tvmaze_seasons,tvmaze_shows,tvmaze_search_cache,embedding_provider_permits,embedding_provider_cooldowns,connection_shared_entities,article_connections,article_connection_exclusions,article_embeddings,article_connection_jobs,identification_jobs, identification_provider_health, screenshot_cleanup, article_screenshots, entity_completions, article_entities, entities, articles, categories, accounts");
    owner = UUID.randomUUID();
    other = UUID.randomUUID();
    for (var account : List.of(owner, other))
      jdbc.update(
          "insert into accounts(id,created_at) values (?,?)",
          account,
          java.sql.Timestamp.from(Instant.now()));
  }

  @Test
  void saveSanitizeReadAndExplicitlyUpdatePreservesArticleText() throws Exception {
    var raw =
        "<article><h1>Heading</h1><custom>Every word survives.</custom><p onclick='evil()'>Article 世界.</p><script>evil()</script><img src='https://tracker.test/'></article>";
    var created =
        send(
            "POST",
            "",
            owner,
            Map.of("url", "https://example.test/article", "title", "Title", "content", raw));
    assertThat(created.statusCode()).isEqualTo(201);
    var saved = body(created).get("article");
    var id = saved.get("id").asText();
    assertThat(body(created).get("outcome").asText()).isEqualTo("CREATED");
    assertThat(saved.get("version").asLong()).isZero();
    assertThat(saved.has("accountId")).isFalse();
    var clean = saved.get("content").asText();
    assertThat(clean)
        .contains("Every word survives.", "Article 世界.")
        .doesNotContain("evil", "<script", "<img", "onclick");
    assertThat(
            jdbc.queryForObject(
                "select content from articles where id=?", String.class, UUID.fromString(id)))
        .isEqualTo(clean);
    assertThat(body(send("GET", "/" + id, owner, null))).isEqualTo(saved);
    var updated =
        send(
            "PATCH",
            "/" + id,
            owner,
            Map.of("version", 0, "title", "Updated", "content", "<p>New text</p>"));
    assertThat(updated.statusCode()).isEqualTo(200);
    assertThat(body(updated).get("version").asLong()).isEqualTo(1);
    assertThat(body(send("GET", "/" + id, owner, null)).get("content").asText())
        .isEqualTo("<p>New text</p>");
    assertThat(
            jdbc.queryForObject(
                "select title from articles where id=?", String.class, UUID.fromString(id)))
        .isEqualTo("Updated");
    var noChange = send("PATCH", "/" + id, owner, Map.of("version", 1, "title", "Updated"));
    assertThat(noChange.statusCode()).isEqualTo(200);
    assertThat(body(noChange).get("version").asLong()).isEqualTo(1);
    assertThat(body(noChange).get("savedAt")).isEqualTo(saved.get("savedAt"));
  }

  @Test
  void duplicatePromptsThenExplicitUpdateAndStaleVersionCannotOverwrite() throws Exception {
    var created =
        send("POST", "", owner, article("https://example.test/duplicate", "<p>Original</p>"));
    var id = body(created).get("article").get("id").asText();
    var duplicate =
        send("POST", "", owner, article("https://example.test/duplicate", "<p>Replacement</p>"));
    assertError(duplicate, 409, "DUPLICATE_URL");
    assertThat(body(duplicate).get("existingArticleId").asText()).isEqualTo(id);
    assertThat(body(send("GET", "/" + id, owner, null)).get("content").asText())
        .isEqualTo("<p>Original</p>");
    assertThat(
            send("PATCH", "/" + id, owner, Map.of("version", 0, "content", "<p>Confirmed</p>"))
                .statusCode())
        .isEqualTo(200);
    assertError(
        send("PATCH", "/" + id, owner, Map.of("version", 0, "content", "<p>Stale</p>")),
        409,
        "VERSION_CONFLICT");
    assertThat(body(send("GET", "/" + id, owner, null)).get("content").asText())
        .isEqualTo("<p>Confirmed</p>");
    assertThat(jdbc.queryForObject("select count(*) from articles", Integer.class)).isEqualTo(1);
  }

  @Test
  void linkOnlySaveEnrichesOnceWithoutPromptAndNeverClearsSavedContent() throws Exception {
    var link =
        send(
            "POST",
            "",
            owner,
            Map.of("url", "https://example.test/link", "title", "Original title"));
    var id = body(link).get("article").get("id").asText();
    assertThat(body(link).get("article").get("content").isNull()).isTrue();
    var reused =
        send("POST", "", owner, Map.of("url", "https://example.test/link", "title", "Other title"));
    assertThat(body(reused).get("outcome").asText()).isEqualTo("UNCHANGED");
    var enriched = send("POST", "", owner, article("https://example.test/link", "<p>Enriched</p>"));
    assertThat(enriched.statusCode()).isEqualTo(200);
    assertThat(body(enriched).get("outcome").asText()).isEqualTo("ENRICHED");
    assertThat(body(enriched).get("article").get("id").asText()).isEqualTo(id);
    assertThat(body(enriched).get("article").get("version").asLong()).isEqualTo(1);
    assertThat(body(enriched).get("article").get("title").asText()).isEqualTo("Original title");
    var urlOnly =
        send("POST", "", owner, Map.of("url", "https://example.test/link", "title", "Changed"));
    assertThat(body(urlOnly).get("outcome").asText()).isEqualTo("UNCHANGED");
    assertThat(body(send("GET", "/" + id, owner, null)).get("content").asText())
        .isEqualTo("<p>Enriched</p>");
  }

  @Test
  void ownershipAndMissingArticleAreIndistinguishable() throws Exception {
    var created = send("POST", "", owner, article("https://example.test/a", "text"));
    var id = body(created).get("article").get("id").asText();
    for (var path : List.of("/" + id, "/" + UUID.randomUUID())) {
      assertError(send("GET", path, other, null), 404, "ARTICLE_NOT_FOUND");
      assertError(
          send("PATCH", path, other, Map.of("version", 0, "title", "Stolen")),
          404,
          "ARTICLE_NOT_FOUND");
    }
    assertThat(
            send("POST", "", other, article("https://example.test/a", "Other account"))
                .statusCode())
        .isEqualTo(201);
    assertThat(jdbc.queryForObject("select count(*) from articles", Integer.class)).isEqualTo(2);
  }

  @Test
  void temporaryAccountCanSaveReadHandleDuplicateExplicitlyUpdateAndListWithoutSignIn()
      throws Exception {
    var created =
        send(
            "POST", "", null, article("https://example.test/temporary", "<p>Original article</p>"));
    assertThat(created.statusCode()).isEqualTo(201);
    var id = body(created).get("article").get("id").asText();
    assertThat(body(send("GET", "/" + id, null, null)).get("content").asText())
        .isEqualTo("<p>Original article</p>");
    assertError(
        send("POST", "", null, article("https://example.test/temporary", "<p>Replacement</p>")),
        409,
        "DUPLICATE_URL");
    assertThat(body(send("GET", "/" + id, null, null)).get("version").asLong()).isZero();
    var updated =
        send(
            "PATCH",
            "/" + id,
            null,
            Map.of("version", 0, "content", "<p>Explicit replacement</p>"));
    assertThat(updated.statusCode()).isEqualTo(200);
    assertThat(body(updated).get("version").asLong()).isEqualTo(1);
    assertThat(body(send("GET", "", null, null)).get("items").get(0).get("id").asText())
        .isEqualTo(id);
    assertThat(
            jdbc.queryForObject(
                "select account_id from articles where id=?", UUID.class, UUID.fromString(id)))
        .isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000001"));
    assertThat(
            jdbc.queryForObject(
                "select content from articles where id=?", String.class, UUID.fromString(id)))
        .isEqualTo("<p>Explicit replacement</p>");
    assertError(send("GET", "/" + id, other, null), 404, "ARTICLE_NOT_FOUND");
  }

  @Test
  void invalidRequestsAndOversizedContentNeverPersistOrOverwrite() throws Exception {
    for (var payload :
        List.of(
            Map.of("url", " https://example.test/", "title", "Title"),
            Map.of("url", "https://example.test/", "title", " "),
            Map.of("url", "javascript:evil()", "title", "Title"),
            Map.of(
                "url",
                "https://example.test/",
                "title",
                "x".repeat(properties.maxTitleCodePoints() + 1)))) {
      assertError(send("POST", "", owner, payload), 400, "INVALID_REQUEST");
    }
    assertError(
        send(
            "POST",
            "",
            owner,
            article("https://example.test/large", "x".repeat(properties.maxContentBytes() + 1))),
        413,
        "CONTENT_TOO_LARGE");
    assertError(
        send(
            "POST",
            "",
            owner,
            article("https://example.test/expanded", "&".repeat(properties.maxContentBytes() / 4))),
        413,
        "CONTENT_TOO_LARGE");
    assertThat(jdbc.queryForObject("select count(*) from articles", Integer.class)).isZero();
    var id =
        body(send("POST", "", owner, article("https://example.test/valid", "<p>Keep me</p>")))
            .get("article")
            .get("id")
            .asText();
    for (var content : List.of("", " ", "<script>bad()</script>"))
      assertError(
          send("PATCH", "/" + id, owner, Map.of("version", 0, "content", content)),
          400,
          "INVALID_REQUEST");
    assertThat(sendRaw("PATCH", "/" + id, owner, "{\"version\":0,\"content\":null}").statusCode())
        .isEqualTo(400);
    assertThat(
            send("PATCH", "/" + id, owner, Map.of("version", 0, "url", "https://changed.test/"))
                .statusCode())
        .isEqualTo(400);
    assertThat(body(send("GET", "/" + id, owner, null)).get("content").asText())
        .isEqualTo("<p>Keep me</p>");
    for (var invalid :
        List.of(
            "{\"version\":0,\"title\":null}",
            "{\"version\":0.5,\"title\":\"Changed\"}",
            "{\"title\":\"Changed\"}",
            "{\"version\":-1,\"title\":\"Changed\"}",
            "{\"version\":0}",
            "{broken")) {
      assertError(sendRaw("PATCH", "/" + id, owner, invalid), 400, "INVALID_REQUEST");
    }
    assertError(
        send(
            "PATCH",
            "/" + id,
            owner,
            Map.of(
                "version",
                0,
                "title",
                "Must not be committed",
                "content",
                "x".repeat(properties.maxContentBytes() + 1))),
        413,
        "CONTENT_TOO_LARGE");
    var unchanged = body(send("GET", "/" + id, owner, null));
    assertThat(unchanged.get("title").asText()).isEqualTo("Article");
    assertThat(unchanged.get("version").asLong()).isZero();
    assertError(
        send(
            "POST",
            "",
            owner,
            Map.of("url", "https://example.test/spoof", "title", "Spoof", "accountId", other)),
        400,
        "INVALID_REQUEST");
  }

  @Test
  void exactUrlsRemainDistinctAndConcurrentSavesProduceOneRecord() throws Exception {
    for (var url :
        List.of(
            "https://example.test/x", "https://example.test/x/", "https://example.test/x?utm=a"))
      assertThat(send("POST", "", owner, article(url, "text")).statusCode()).isEqualTo(201);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var latch = new CountDownLatch(1);
      var futures = new ArrayList<Future<HttpResponse<String>>>();
      for (int i = 0; i < 2; i++)
        futures.add(
            executor.submit(
                () -> {
                  latch.await();
                  return send("POST", "", owner, article("https://example.test/race", "race text"));
                }));
      latch.countDown();
      var responses = new ArrayList<Integer>();
      for (var future : futures) responses.add(future.get(30, TimeUnit.SECONDS).statusCode());
      assertThat(responses).containsExactlyInAnyOrder(201, 409);
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from articles where url=?",
                Integer.class,
                "https://example.test/race"))
        .isEqualTo(1);
  }

  @Test
  void concurrentExplicitUpdatesAcceptOnlyOneVersion() throws Exception {
    var id =
        body(send("POST", "", owner, article("https://example.test/update-race", "original")))
            .get("article")
            .get("id")
            .asText();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var latch = new CountDownLatch(1);
      var tasks = new ArrayList<Future<HttpResponse<String>>>();
      for (var title : List.of("First", "Second"))
        tasks.add(
            executor.submit(
                () -> {
                  latch.await();
                  return send("PATCH", "/" + id, owner, Map.of("version", 0, "title", title));
                }));
      latch.countDown();
      var statuses = new ArrayList<Integer>();
      for (var task : tasks) statuses.add(task.get(30, TimeUnit.SECONDS).statusCode());
      assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    }
    assertThat(body(send("GET", "/" + id, owner, null)).get("version").asLong()).isEqualTo(1);
  }

  private Map<String, Object> article(String url, String content) {
    return Map.of("url", url, "title", "Article", "content", content);
  }

  private HttpResponse<String> send(String method, String path, UUID account, Object payload)
      throws Exception {
    return sendRaw(
        method, path, account, payload == null ? null : json.writeValueAsString(payload));
  }

  private HttpResponse<String> sendRaw(String method, String path, UUID account, String payload)
      throws Exception {
    var request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:"
                        + environment.getProperty("local.server.port")
                        + "/api/v1/articles"
                        + path))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .method(
                method,
                payload == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(payload));
    if (account != null) request.header("X-Test-Account", account.toString());
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode body(HttpResponse<String> response) {
    return json.readTree(response.body());
  }

  private void assertError(HttpResponse<String> response, int status, String reason) {
    assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
    var error = body(response);
    assertThat(error.get("reason").asText()).isEqualTo(reason);
    assertThat(error.has("timestamp")).isTrue();
    assertThat(error.get("status").asInt()).isEqualTo(status);
    assertThat(error.has("path")).isTrue();
  }
}
