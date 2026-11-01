package com.know.categories.presentation;

import static org.assertj.core.api.Assertions.*;

import com.know.support.BackendIntegrationSupport;
import java.net.URI;
import java.net.http.*;
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
class CategoryManagementIT extends BackendIntegrationSupport {
  private final JdbcTemplate jdbc;
  private final Environment environment;
  private final HttpClient http = HttpClient.newHttpClient();
  private final JsonMapper json = JsonMapper.builder().build();
  private UUID owner;
  private UUID foreign;

  @BeforeEach
  void fixtures() {
    jdbc.execute(
        "truncate identity_profiles,identity_refresh_tokens,identity_codes,identity_sessions,external_account_providers,entity_episode_watches,entity_tv_shows,tvmaze_refresh_jobs,tvmaze_episodes,tvmaze_seasons,tvmaze_shows,tvmaze_search_cache,embedding_provider_permits,embedding_provider_cooldowns,connection_shared_entities,article_connections,article_connection_exclusions,article_embeddings,article_connection_jobs,identification_jobs, identification_provider_health, screenshot_cleanup, article_screenshots, entity_completions,article_entities,entities,articles,categories,accounts");
    owner = UUID.randomUUID();
    foreign = UUID.randomUUID();
    for (var id : List.of(owner, foreign))
      jdbc.update("insert into accounts(id,created_at) values (?,now())", id);
  }

  @Test
  void createAssignRenameProtectDeleteArticleAndManageEmptyCategoryIsACompleteFlow()
      throws Exception {
    var created =
        send("POST", "/categories", owner, Map.of("displayName", " Research ", "color", "#305ea7"));
    assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
    var category = body(created);
    var key = category.get("key").asText();
    assertThat(category.get("displayName").asText()).isEqualTo("Research");
    assertThat(category.get("articleCount").asLong()).isZero();
    assertThat(category.get("version").asLong()).isZero();
    assertThat(created.headers().firstValue("Location").orElseThrow())
        .endsWith("/categories/" + key);
    var article = save();
    var id = article.get("id").asText();
    assertThat(
            send("PATCH", "/articles/" + id, owner, Map.of("version", 0, "category", key))
                .statusCode())
        .isEqualTo(200);
    var linked =
        send(
            "POST",
            "/articles/" + id + "/entities",
            owner,
            Map.of("type", "BOOK", "title", "Keep this entity"));
    assertThat(linked.statusCode()).as(linked.body()).isIn(200, 201);
    var entityId = body(linked).get("entity").get("id").asText();
    assertThat(
            send("POST", "/entities/" + entityId + "/completions", owner, Map.of("version", 0))
                .statusCode())
        .isEqualTo(201);
    assertThat(catalog(key).get("articleCount").asLong()).isEqualTo(1);
    var renamed =
        send(
            "PATCH",
            "/categories/" + key,
            owner,
            Map.of("version", 0, "displayName", "Reading", "color", "#2876dc"));
    assertThat(renamed.statusCode()).as(renamed.body()).isEqualTo(200);
    assertThat(body(renamed).get("key").asText()).isEqualTo(key);
    assertThat(body(send("GET", "/articles/" + id, owner, null)).get("category").asText())
        .isEqualTo(key);
    assertThat(body(send("GET", "/articles/" + id + "/entities", owner, null)).get("items").size())
        .isEqualTo(1);
    reason(
        send("PATCH", "/categories/" + key, owner, Map.of("version", 1, "enabled", false)),
        409,
        "CATEGORY_IN_USE");
    reason(
        send("DELETE", "/categories/" + key + "?version=1", owner, null), 409, "CATEGORY_IN_USE");
    reason(send("DELETE", "/articles/" + id + "?version=0", owner, null), 409, "VERSION_CONFLICT");
    assertThat(send("DELETE", "/articles/" + id + "?version=1", foreign, null).statusCode())
        .isEqualTo(404);
    var deleted = send("DELETE", "/articles/" + id + "?version=1", owner, null);
    assertThat(deleted.statusCode()).as(deleted.body()).isEqualTo(204);
    assertThat(deleted.body()).isEmpty();
    assertThat(send("GET", "/articles/" + id, owner, null).statusCode()).isEqualTo(404);
    assertThat(body(send("GET", "/articles", owner, null)).get("items").size()).isZero();
    assertThat(body(send("GET", "/entities?type=BOOK", owner, null)).get("items").size())
        .isEqualTo(1);
    assertThat(
            body(send("GET", "/entities/" + entityId + "/completions", owner, null))
                .get("items")
                .size())
        .isEqualTo(1);
    assertThat(catalog(key).get("articleCount").asLong()).isZero();
    assertThat(catalog(key).get("enabled").asBoolean()).isTrue();
    assertThat(
            send("PATCH", "/categories/" + key, owner, Map.of("version", 1, "enabled", false))
                .statusCode())
        .isEqualTo(200);
    assertThat(catalog(key).get("enabled").asBoolean()).isFalse();
    assertThat(
            send("PATCH", "/categories/" + key, owner, Map.of("version", 2, "enabled", true))
                .statusCode())
        .isEqualTo(200);
    assertThat(send("DELETE", "/categories/" + key + "?version=3", owner, null).statusCode())
        .isEqualTo(204);
    assertThat(
            send(
                    "PATCH",
                    "/categories/" + key,
                    owner,
                    Map.of("version", 3, "displayName", "Missing"))
                .statusCode())
        .isEqualTo(404);
  }

  @Test
  void namesIsolationDefaultEditingSeedingAndValidationAreEnforcedThroughHttp() throws Exception {
    var created =
        body(
            send(
                "POST",
                "/categories",
                owner,
                Map.of("displayName", "Research", "color", "#112233")));
    var key = created.get("key").asText();
    reason(
        send("POST", "/categories", owner, Map.of("displayName", " research ", "color", "#112233")),
        409,
        "DUPLICATE_CATEGORY_NAME");
    assertThat(
            send(
                    "POST",
                    "/categories",
                    foreign,
                    Map.of("displayName", "Research", "color", "#112233"))
                .statusCode())
        .isEqualTo(201);
    reason(
        send(
            "PATCH", "/categories/" + key, foreign, Map.of("version", 0, "displayName", "Foreign")),
        404,
        "RESOURCE_NOT_FOUND");
    reason(
        send("DELETE", "/categories/" + key + "?version=0", foreign, null),
        404,
        "RESOURCE_NOT_FOUND");
    reason(
        send("PATCH", "/categories/" + key, owner, Map.of("version", 0, "displayName", " books ")),
        409,
        "DUPLICATE_CATEGORY_NAME");
    var edited =
        send(
            "PATCH",
            "/categories/BOOKS",
            owner,
            Map.of("version", 0, "displayName", "My books", "color", "#ABCDEF"));
    assertThat(edited.statusCode()).as(edited.body()).isEqualTo(200);
    assertThat(
            send("PATCH", "/categories/BOOKS", owner, Map.of("version", 1, "enabled", false))
                .statusCode())
        .isEqualTo(200);
    save(); // Normal article saving runs default seeding again.
    assertThat(catalog("BOOKS").get("displayName").asText()).isEqualTo("My books");
    assertThat(catalog("BOOKS").get("enabled").asBoolean()).isFalse();
    reason(
        send("DELETE", "/categories/BOOKS?version=2", owner, null),
        409,
        "DEFAULT_CATEGORY_PROTECTED");
    reason(
        send("PATCH", "/categories/OTHER", owner, Map.of("version", 0, "enabled", false)),
        409,
        "CATEGORY_IN_USE");
    for (var payload :
        List.of(
            Map.of("version", 0),
            Map.of("version", 0, "color", "red"),
            Map.of("version", 0, "displayName", " "),
            Map.of("version", 0, "displayName", "a".repeat(101)),
            Map.of("version", 0, "enabled", "false"),
            Map.of("version", 0, "extra", true)))
      assertThat(send("PATCH", "/categories/" + key, owner, payload).statusCode()).isEqualTo(400);
    assertThat(
            send(
                    "PATCH",
                    "/categories/" + key,
                    owner,
                    json.readTree("{\"version\":0,\"displayName\":null}"))
                .statusCode())
        .isEqualTo(400);
    var noop =
        send("PATCH", "/categories/" + key, owner, Map.of("version", 0, "displayName", "Research"));
    assertThat(body(noop).get("version").asLong()).isZero();
  }

  @Test
  void protectsEmptyFallbackRejectsStaleWritesAndValidatesCreationWithoutCoercion()
      throws Exception {
    catalog("OTHER");
    reason(
        send("PATCH", "/categories/OTHER", owner, Map.of("version", 0, "enabled", false)),
        409,
        "CATEGORY_REQUIRED");
    for (var payload :
        List.of(
            Map.of("displayName", 123, "color", "#112233"),
            Map.of("displayName", "Name"),
            Map.of("displayName", "Name", "color", "#112233", "enabled", true)))
      assertThat(send("POST", "/categories", owner, payload).statusCode()).isEqualTo(400);
    var created =
        body(
            send(
                "POST",
                "/categories",
                owner,
                Map.of("displayName", "Versioned", "color", "#112233")));
    var key = created.get("key").asText();
    assertThat(
            send(
                    "PATCH",
                    "/categories/" + key,
                    owner,
                    Map.of("version", 0, "displayName", "New name"))
                .statusCode())
        .isEqualTo(200);
    reason(
        send("PATCH", "/categories/" + key, owner, Map.of("version", 0, "enabled", false)),
        409,
        "VERSION_CONFLICT");
    reason(
        send("DELETE", "/categories/" + key + "?version=0", owner, null), 409, "VERSION_CONFLICT");
    assertThat(catalog(key).get("enabled").asBoolean()).isTrue();
    var article = save().get("id").asText();
    assertThat(send("DELETE", "/articles/" + article, owner, null).statusCode()).isEqualTo(400);
    assertThat(send("DELETE", "/articles/" + article + "?version=-1", owner, null).statusCode())
        .isEqualTo(400);
    assertThat(send("GET", "/articles/" + article, owner, null).statusCode()).isEqualTo(200);
  }

  @Test
  void concurrentCategoryRemovalAndAssignmentNeverLeaveBrokenReferences() throws Exception {
    var key =
        body(send(
                "POST",
                "/categories",
                owner,
                Map.of("displayName", "Removable", "color", "#112233")))
            .get("key")
            .asText();
    var id = save().get("id").asText();
    var results =
        race(
            () -> send("DELETE", "/categories/" + key + "?version=0", owner, null),
            () -> send("PATCH", "/articles/" + id, owner, Map.of("version", 0, "category", key)));
    assertThat(results.stream().map(HttpResponse::statusCode).toList())
        .satisfiesAnyOf(
            statuses -> assertThat(statuses).isEqualTo(List.of(204, 400)),
            statuses -> assertThat(statuses).isEqualTo(List.of(409, 200)));
    var article = body(send("GET", "/articles/" + id, owner, null));
    if (article.get("category").asText().equals(key))
      assertThat(catalog(key).get("articleCount").asLong()).isEqualTo(1);
    else assertThat(article.get("category").asText()).isEqualTo("OTHER");
  }

  @Test
  void concurrentAssignmentCannotBypassDisablingAndConcurrentNamesRemainUnique() throws Exception {
    var category =
        body(send("POST", "/categories", owner, Map.of("displayName", "Race", "color", "#112233")));
    var key = category.get("key").asText();
    var article = save().get("id").asText();
    var results =
        race(
            () ->
                send("PATCH", "/categories/" + key, owner, Map.of("version", 0, "enabled", false)),
            () ->
                send(
                    "PATCH", "/articles/" + article, owner, Map.of("version", 0, "category", key)));
    assertThat(results.stream().filter(r -> r.statusCode() == 200).count()).isEqualTo(1);
    assertThat(results.stream().map(HttpResponse::statusCode).toList())
        .allMatch(s -> s == 200 || s == 400 || s == 409);
    var current = catalog(key);
    assertThat(current.get("enabled").asBoolean() || current.get("articleCount").asLong() == 0)
        .isTrue();
    var duplicates =
        race(
            () ->
                send(
                    "POST",
                    "/categories",
                    owner,
                    Map.of("displayName", "Concurrent", "color", "#112233")),
            () ->
                send(
                    "POST",
                    "/categories",
                    owner,
                    Map.of("displayName", " concurrent ", "color", "#112233")));
    assertThat(duplicates.stream().map(HttpResponse::statusCode).toList())
        .containsExactlyInAnyOrder(201, 409);
  }

  private List<HttpResponse<String>> race(
      Callable<HttpResponse<String>> one, Callable<HttpResponse<String>> two) throws Exception {
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var start = new CountDownLatch(1);
      var a =
          pool.submit(
              () -> {
                start.await();
                return one.call();
              });
      var b =
          pool.submit(
              () -> {
                start.await();
                return two.call();
              });
      start.countDown();
      return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
    }
  }

  private JsonNode save() throws Exception {
    var response =
        send(
            "POST",
            "/articles",
            owner,
            Map.of(
                "url",
                "https://phase5.test/" + UUID.randomUUID(),
                "title",
                "Saved article",
                "content",
                "<p>Preserve this text</p>"));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return body(response).get("article");
  }

  private JsonNode catalog(String key) throws Exception {
    var response = send("GET", "/categories", owner, null);
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    for (var item : body(response).get("items"))
      if (item.get("key").asText().equals(key)) return item;
    throw new AssertionError("Missing category " + key);
  }

  private void reason(HttpResponse<String> response, int status, String reason) {
    assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
    assertThat(body(response).get("reason").asText()).isEqualTo(reason);
  }

  private JsonNode body(HttpResponse<String> response) {
    return json.readTree(response.body());
  }

  private HttpResponse<String> send(String method, String path, UUID account, Object payload)
      throws Exception {
    var request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:"
                        + environment.getProperty("local.server.port")
                        + "/api/v1"
                        + path))
            .header("X-Test-Account", account.toString());
    if (payload != null) request.header("Content-Type", "application/json");
    return http.send(
        request
            .method(
                method,
                payload == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
