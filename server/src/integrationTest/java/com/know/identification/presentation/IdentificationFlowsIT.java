package com.know.identification.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.know.identification.application.IdentificationWorker;
import com.know.support.BackendIntegrationSupport;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor(onConstructor_ = @Autowired)
class IdentificationFlowsIT extends BackendIntegrationSupport {
  private final JdbcTemplate jdbc;
  private final Environment environment;
  private final IdentificationWorker worker;
  private final JsonMapper json = JsonMapper.builder().build();
  private final HttpClient http = HttpClient.newHttpClient();
  private UUID owner;
  private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");
  private static final String RESULT =
      """
      {"sufficient":true,"category":"BOOKS","subjects":[{"name":"1984","main":true},{"name":"Animal Farm","main":false}]}
      """;

  @BeforeEach
  void fixtures() {
    jdbc.execute(
        "truncate entity_episode_watches,entity_tv_shows,tvmaze_refresh_jobs,tvmaze_episodes,tvmaze_seasons,tvmaze_shows,tvmaze_search_cache,embedding_provider_permits,embedding_provider_cooldowns,connection_shared_entities,article_connections,article_connection_exclusions,article_embeddings,article_connection_jobs,identification_jobs,identification_provider_health,screenshot_cleanup,article_screenshots,entity_completions,article_entities,entities,articles,categories,accounts");
    entityTestClock.set(NOW);
    IDENTIFICATION.reset();
    owner = UUID.randomUUID();
    jdbc.update(
        "insert into accounts(id,created_at) values (?,?)", owner, java.sql.Timestamp.from(NOW));
  }

  @Test
  void firstSaveQueuesThenIdentifiesMultipleSubjectsWithoutCreatingEntityLinks() throws Exception {
    var article = save("<p>1984 and Animal Farm are central subjects.</p>");
    var id = article.get("id").asText();
    assertThat(status(id).get("status").asText()).isEqualTo("PENDING");
    IDENTIFICATION.result("openrouter", RESULT.replace("\"BOOKS\"", "\"Books\""));
    assertThat(worker.runOne()).isTrue();
    var done = status(id);
    assertThat(done.get("status").asText()).isEqualTo("SUCCEEDED");
    assertThat(done.get("provider").asText()).isEqualTo("openrouter");
    assertThat(done.get("subjects").size()).isEqualTo(2);
    assertThat(done.get("subjects").get(0).get("main").asBoolean()).isTrue();
    var detail = body(send("GET", "/" + id, null));
    assertThat(detail.get("entityName").asText()).isEqualTo("1984");
    assertThat(detail.get("category").asText()).isEqualTo("BOOKS");
    assertThat(done.get("categorySource").asText()).isEqualTo("AI");
    assertThat(jdbc.queryForObject("select count(*) from article_entities", Integer.class))
        .isZero();
    assertThat(IDENTIFICATION.openrouterRequests).hasSize(1);
    assertThat(IDENTIFICATION.groqRequests).isEmpty();
    assertThat(IDENTIFICATION.openrouterRequests.getFirst())
        .doesNotContain("https://", "<p>", "Unrelated source title");
    assertThat(worker.runOne()).isFalse();
    assertThat(send("POST", "/" + id + "/identification", null).statusCode()).isEqualTo(202);
    IDENTIFICATION.result("openrouter", RESULT);
    assertThat(worker.runOne()).isTrue();
    assertThat(status(id).get("status").asText()).isEqualTo("SUCCEEDED");
    var current = body(send("GET", "/" + id, null));
    assertThat(
            send("DELETE", "/" + id + "?version=" + current.get("version").asLong(), null)
                .statusCode())
        .isEqualTo(204);
    assertThat(jdbc.queryForObject("select count(*) from identification_jobs", Integer.class))
        .isZero();
  }

  @Test
  void manualValuesWinEvenWhenEditedDuringProviderCallAndOnlyMissingFieldsAreRequested()
      throws Exception {
    var article = save("<p>Books about dystopia.</p>");
    var id = article.get("id").asText();
    IDENTIFICATION.result(
        "openrouter",
        RESULT,
        () -> {
          try {
            assertThat(
                    send(
                            "PATCH",
                            "/" + id,
                            Map.of("version", 0, "category", "AI", "entityName", "My name"))
                        .statusCode())
                .isEqualTo(200);
          } catch (Exception failure) {
            throw new IllegalStateException(failure);
          }
        });
    worker.runOne();
    var detail = body(send("GET", "/" + id, null));
    assertThat(detail.get("category").asText()).isEqualTo("AI");
    assertThat(detail.get("entityName").asText()).isEqualTo("My name");
    assertThat(status(id).get("subjects").size()).isZero();
    send("POST", "/" + id + "/identification", null);
    worker.runOne();
    assertThat(status(id).get("reason").asText()).isEqualTo("NO_MISSING_FIELDS");
    assertThat(IDENTIFICATION.openrouterRequests).hasSize(1);
    var second = save("<p>Different article about books.</p>");
    var secondId = second.get("id").asText();
    send("PATCH", "/" + secondId, Map.of("version", 0, "category", "DEVELOPMENT"));
    IDENTIFICATION.result("openrouter", RESULT);
    worker.runOne();
    assertThat(body(send("GET", "/" + secondId, null)).get("category").asText())
        .isEqualTo("DEVELOPMENT");
    assertThat(body(send("GET", "/" + secondId, null)).get("entityName").asText())
        .isEqualTo("1984");
    assertThat(IDENTIFICATION.openrouterRequests.getLast()).contains("categoryNeeded\\\":false");
  }

  @Test
  void fallbackQuotaCooldownPersistsAndExpiresWithoutRepeatedProviderCalls() throws Exception {
    var id = save("<p>A book article.</p>").get("id").asText();
    IDENTIFICATION.fail("openrouter", 429, "Free daily limit reached: requests per day exhausted");
    IDENTIFICATION.result("groq", RESULT);
    worker.runOne();
    assertThat(status(id).get("provider").asText()).isEqualTo("groq");
    assertThat(
            jdbc.queryForObject(
                    "select cooldown_until from identification_provider_health where provider='openrouter'",
                    java.sql.Timestamp.class)
                .toInstant())
        .isEqualTo(NOW.plusSeconds(86400));
    send("POST", "/" + id + "/identification", null);
    IDENTIFICATION.fail("groq", 503, "Unavailable");
    worker.runOne();
    assertThat(status(id).get("status").asText()).isEqualTo("FAILED");
    assertThat(IDENTIFICATION.openrouterRequests).hasSize(1);
    assertThat(IDENTIFICATION.groqRequests).hasSize(2); // No hidden SDK retries.
    entityTestClock.set(NOW.plusSeconds(86401));
    send("POST", "/" + id + "/identification", null);
    IDENTIFICATION.result("openrouter", RESULT);
    worker.runOne();
    assertThat(status(id).get("status").asText()).isEqualTo("SUCCEEDED");
    assertThat(IDENTIFICATION.openrouterRequests).hasSize(2);
  }

  @Test
  void threeConsecutiveFailuresOpenCooldownAndSuccessfulCallsResetTheCounter() throws Exception {
    for (int i = 0; i < 3; i++) {
      var id = save("<p>Article number " + i + ".</p>").get("id").asText();
      IDENTIFICATION.fail("openrouter", 503, "Unavailable");
      IDENTIFICATION.result("groq", RESULT);
      worker.runOne();
      assertThat(status(id).get("status").asText()).isEqualTo("SUCCEEDED");
    }
    var next = save("<p>Next article.</p>").get("id").asText();
    IDENTIFICATION.result("groq", RESULT);
    worker.runOne();
    assertThat(status(next).get("provider").asText()).isEqualTo("groq");
    assertThat(IDENTIFICATION.openrouterRequests).hasSize(3);
    assertThat(
            jdbc.queryForObject(
                "select consecutive_failures from identification_provider_health where provider='groq'",
                Integer.class))
        .isZero();
  }

  @Test
  void missingTextSkipsAndExpiredLeaseIsRecoveredFromPostgres() throws Exception {
    var empty = save(null).get("id").asText();
    worker.runOne();
    assertThat(status(empty).get("reason").asText()).isEqualTo("NO_ARTICLE_TEXT");
    var id = save("<p>1984.</p>").get("id").asText();
    jdbc.update(
        "update identification_jobs set status='RUNNING',claim_id=?,lease_until=? where article_id=?",
        UUID.randomUUID(),
        java.sql.Timestamp.from(NOW.minusSeconds(1)),
        UUID.fromString(id));
    IDENTIFICATION.result("openrouter", RESULT);
    worker.runOne();
    assertThat(status(id).get("status").asText()).isEqualTo("SUCCEEDED");
    assertThat(IDENTIFICATION.openrouterRequests).hasSize(1);
    assertThat(sendAs(UUID.randomUUID(), "GET", "/" + id + "/identification", null).statusCode())
        .isEqualTo(404);
    assertThat(sendAs(UUID.randomUUID(), "POST", "/" + id + "/identification", null).statusCode())
        .isEqualTo(404);
  }

  @Test
  void largeArticleUsesAnotherChunkOnlyWhenNeededAndStopsEarly() throws Exception {
    var id = save("<p>" + "Text before subjects. ".repeat(1000) + "</p>").get("id").asText();
    IDENTIFICATION.result("openrouter", "{\"sufficient\":false,\"category\":null,\"subjects\":[]}");
    IDENTIFICATION.result("openrouter", RESULT);
    worker.runOne();
    assertThat(status(id).get("status").asText()).isEqualTo("SUCCEEDED");
    assertThat(IDENTIFICATION.openrouterRequests).hasSize(2);
    assertThat(IDENTIFICATION.groqRequests).isEmpty();
  }

  @Test
  void invalidOutputFallsBackAndContentChangedDuringWorkIsNeverApplied() throws Exception {
    var id = save("<p>Original article.</p>").get("id").asText();
    IDENTIFICATION.result(
        "openrouter", "{\"sufficient\":true,\"category\":\"INVENTED\",\"subjects\":[]}");
    IDENTIFICATION.result(
        "groq",
        RESULT,
        () -> {
          try {
            send("PATCH", "/" + id, Map.of("version", 0, "content", "<p>Entirely different.</p>"));
          } catch (Exception failure) {
            throw new IllegalStateException(failure);
          }
        });
    worker.runOne();
    var detail = body(send("GET", "/" + id, null));
    assertThat(detail.get("category").asText()).isEqualTo("OTHER");
    assertThat(detail.get("entityName").isNull()).isTrue();
    assertThat(status(id).get("status").asText()).isEqualTo("FAILED");
  }

  @Test
  void recoveredWorkerFencesOffTheOldWorkerResult() throws Exception {
    var id = save("<p>A book review.</p>").get("id").asText();
    var newer = RESULT.replace("1984", "Latest subject");
    IDENTIFICATION.result(
        "openrouter",
        RESULT,
        () -> {
          entityTestClock.set(NOW.plusSeconds(601));
          IDENTIFICATION.result("openrouter", newer);
          assertThat(worker.runOne()).isTrue();
        });
    assertThat(worker.runOne()).isTrue();
    assertThat(status(id).get("status").asText()).isEqualTo("SUCCEEDED");
    assertThat(body(send("GET", "/" + id, null)).get("entityName").asText())
        .isEqualTo("Latest subject");
    assertThat(IDENTIFICATION.openrouterRequests).hasSize(2);
  }

  @Test
  void manualInputDuringChunkProcessingStopsFurtherRequests() throws Exception {
    var id = save("<p>" + "Long article text. ".repeat(1000) + "</p>").get("id").asText();
    IDENTIFICATION.result(
        "openrouter",
        "{\"sufficient\":false,\"category\":null,\"subjects\":[]}",
        () -> {
          try {
            assertThat(
                    send(
                            "PATCH",
                            "/" + id,
                            Map.of("version", 0, "category", "OTHER", "entityName", "My subject"))
                        .statusCode())
                .isEqualTo(200);
          } catch (Exception failure) {
            throw new IllegalStateException(failure);
          }
        });
    worker.runOne();
    assertThat(status(id).get("reason").asText()).isEqualTo("NO_MISSING_FIELDS");
    assertThat(IDENTIFICATION.openrouterRequests).hasSize(1);
    assertThat(IDENTIFICATION.groqRequests).isEmpty();
  }

  @Test
  void deletionDuringProviderCallCannotRecreateTheArticleOrJob() throws Exception {
    var id = save("<p>A book review.</p>").get("id").asText();
    IDENTIFICATION.result(
        "openrouter",
        RESULT,
        () -> {
          try {
            assertThat(send("DELETE", "/" + id + "?version=0", null).statusCode()).isEqualTo(204);
          } catch (Exception failure) {
            throw new IllegalStateException(failure);
          }
        });
    assertThat(worker.runOne()).isTrue();
    assertThat(send("GET", "/" + id, null).statusCode()).isEqualTo(404);
    assertThat(jdbc.queryForObject("select count(*) from identification_jobs", Integer.class))
        .isZero();
  }

  @Test
  void existingExplicitLinksSurviveIdentificationAndManualClearingIsProtected() throws Exception {
    var id = save("<p>A review of 1984.</p>").get("id").asText();
    assertThat(
            send("POST", "/" + id + "/entities", Map.of("type", "BOOK", "title", "Dune"))
                .statusCode())
        .isEqualTo(201);
    var patch = new java.util.HashMap<String, Object>();
    patch.put("version", 0);
    patch.put("entityName", null);
    patch.put("category", "OTHER");
    assertThat(send("PATCH", "/" + id, patch).statusCode()).isEqualTo(200);
    worker.runOne();
    assertThat(status(id).get("reason").asText()).isEqualTo("NO_MISSING_FIELDS");
    assertThat(body(send("GET", "/" + id, null)).get("entityName").isNull()).isTrue();
    assertThat(jdbc.queryForObject("select count(*) from article_entities", Integer.class))
        .isEqualTo(1);
    assertThat(IDENTIFICATION.openrouterRequests).isEmpty();
  }

  private JsonNode save(String content) throws Exception {
    var fields = new java.util.HashMap<String, Object>();
    fields.put("url", "https://example.test/" + UUID.randomUUID());
    fields.put("title", "Unrelated source title");
    if (content != null) fields.put("content", content);
    var response = send("POST", "", fields);
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return body(response).get("article");
  }

  private JsonNode status(String id) throws Exception {
    return body(send("GET", "/" + id + "/identification", null));
  }

  private JsonNode body(HttpResponse<String> response) {
    return json.readTree(response.body());
  }

  private HttpResponse<String> send(String method, String path, Object data) throws Exception {
    return sendAs(owner, method, path, data);
  }

  private HttpResponse<String> sendAs(UUID account, String method, String path, Object data)
      throws Exception {
    var request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:"
                        + environment.getProperty("local.server.port")
                        + "/api/v1/articles"
                        + path))
            .header("X-Test-Account", account.toString())
            .header("Content-Type", "application/json")
            .method(
                method,
                data == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data)))
            .build();
    return http.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
