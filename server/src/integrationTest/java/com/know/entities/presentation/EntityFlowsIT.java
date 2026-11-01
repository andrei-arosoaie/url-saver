package com.know.entities.presentation;

import static org.assertj.core.api.Assertions.*;

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
class EntityFlowsIT extends BackendIntegrationSupport {
  private final JdbcTemplate jdbc;
  private final Environment environment;
  private final HttpClient http = HttpClient.newHttpClient();
  private final JsonMapper json = JsonMapper.builder().build();
  private UUID owner, other;
  private static final Instant NOW = Instant.parse("2026-10-25T18:30:00Z");

  @BeforeEach
  void fixtures() {
    jdbc.execute(
        "truncate identity_profiles,identity_refresh_tokens,identity_codes,identity_sessions,external_account_providers,entity_episode_watches,entity_tv_shows,tvmaze_refresh_jobs,tvmaze_episodes,tvmaze_seasons,tvmaze_shows,tvmaze_search_cache,embedding_provider_permits,embedding_provider_cooldowns,connection_shared_entities,article_connections,article_connection_exclusions,article_embeddings,article_connection_jobs,identification_jobs, identification_provider_health, screenshot_cleanup, article_screenshots, entity_completions,article_entities,entities,articles,categories,accounts");
    owner = UUID.randomUUID();
    other = UUID.randomUUID();
    for (var account : List.of(owner, other))
      jdbc.update("insert into accounts(id,created_at) values (?,now())", account);
    entityTestClock.set(NOW);
  }

  @Test
  void captureLinkRenameCompleteCorrectHistoryBrowseYearsAndUnlinkPreservesEveryRecord()
      throws Exception {
    var a = article("first");
    var b = article("second");
    var added =
        request(
            201,
            "POST",
            "/articles/" + a + "/entities",
            owner,
            Map.of("type", "BOOK", "title", "Dune"));
    var id = added.get("entity").get("id").asText();
    var base = "/entities/" + id;
    assertThat(added.get("outcome").asText()).isEqualTo("CREATED");
    assertThat(added.get("entity").get("status").asText()).isEqualTo("TO_READ");
    var completed =
        request(
            200,
            "PATCH",
            base,
            owner,
            Map.of("version", 0, "status", "READ", "completedAt", "2025-12-31T23:59:59.999999Z"));
    assertThat(completed.get("version").asLong()).isEqualTo(1);
    assertThat(completed.get("totalCompletionCount").asLong()).isEqualTo(1);
    var reused =
        request(
            200,
            "POST",
            "/articles/" + b + "/entities",
            owner,
            Map.of("type", "BOOK", "title", "Dune"));
    assertThat(reused.get("entity").get("id").asText()).isEqualTo(id);
    assertThat(reused.get("entity").get("totalCompletionCount").asLong()).isEqualTo(1);
    var linkedAt = reused.get("link").get("linkedAt").asText();
    entityTestClock.set(NOW.plusSeconds(10));
    var unchanged =
        request(
            200,
            "POST",
            "/articles/" + b + "/entities",
            owner,
            Map.of("type", "BOOK", "title", "Dune"));
    assertThat(unchanged.get("outcome").asText()).isEqualTo("UNCHANGED");
    assertThat(unchanged.get("link").get("linkedAt").asText()).isEqualTo(linkedAt);
    var duplicate =
        request(409, "POST", "/entities", owner, Map.of("type", "BOOK", "title", "Dune"));
    assertThat(duplicate.get("existingEntityId").asText()).isEqualTo(id);
    var renamed =
        request(
            200,
            "PATCH",
            base,
            owner,
            Map.of("version", 1, "title", "Dune revised", "status", "READ"));
    assertThat(renamed.get("version").asLong()).isEqualTo(2);
    assertThat(renamed.get("createdAt").asText()).isEqualTo(NOW.toString());
    assertThat(renamed.get("totalCompletionCount").asLong()).isEqualTo(1);
    request(
        201,
        "POST",
        base + "/completions",
        owner,
        Map.of("version", 2, "completedAt", "2026-01-01T00:00:00Z"));
    var auto = request(201, "POST", base + "/completions", owner, Map.of("version", 3));
    assertThat(auto.get("completion").get("completedAt").asText())
        .isEqualTo(NOW.plusSeconds(10).toString());
    assertThat(auto.get("entity").get("version").asLong()).isEqualTo(4);
    var years = request(200, "GET", "/entities/years?type=BOOK", owner, null).get("years");
    assertThat(years.get(0).get("year").asInt()).isEqualTo(2026);
    assertThat(years.get(0).get("entityCount").asLong()).isEqualTo(1);
    assertThat(years.get(0).get("completionCount").asLong()).isEqualTo(2);
    assertThat(years.get(1).get("year").asInt()).isEqualTo(2025);
    var in2025 =
        request(200, "GET", "/entities?type=BOOK&completionYear=2025", owner, null).get("items");
    assertThat(in2025.size()).isEqualTo(1);
    assertThat(in2025.get(0).get("completionCount").asLong()).isEqualTo(1);
    assertThat(in2025.get(0).get("totalCompletionCount").asLong()).isEqualTo(3);
    var history = allItems(base + "/completions?limit=1", owner);
    assertThat(history).hasSize(3);
    assertThat(history.stream().map(e -> e.get("id").asText()).distinct().count()).isEqualTo(3);
    var oldest = history.getLast();
    var eventPath = base + "/completions/" + oldest.get("id").asText();
    var corrected =
        request(
            200,
            "PATCH",
            eventPath,
            owner,
            Map.of("version", 0, "completedAt", "2024-12-31T23:59:59Z"));
    assertThat(corrected.get("createdAt").asText()).isEqualTo(oldest.get("createdAt").asText());
    request(
        200,
        "PATCH",
        eventPath,
        owner,
        Map.of("version", 1, "completedAt", "2024-12-31T23:59:59Z"));
    assertThat(request(200, "GET", base, owner, null).get("version").asLong()).isEqualTo(5);
    var stale = request(409, "DELETE", eventPath + "?version=0", owner, null);
    assertThat(stale.get("reason").asText()).isEqualTo("VERSION_CONFLICT");
    request(204, "DELETE", eventPath + "?version=1", owner, null);
    var detail = request(200, "GET", base, owner, null);
    assertThat(detail.get("status").asText()).isEqualTo("READ");
    assertThat(detail.get("version").asLong()).isEqualTo(6);
    assertThat(detail.get("totalCompletionCount").asLong()).isEqualTo(2);
    var sources = allItems(base + "/articles?limit=1", owner);
    assertThat(sources.stream().map(e -> e.get("id").asText()).toList())
        .containsExactlyInAnyOrder(a, b);
    assertThat(sources).allSatisfy(e -> assertThat(e.has("content")).isFalse());
    request(
        200,
        "PATCH",
        "/articles/" + a,
        owner,
        Map.of("version", 0, "category", "MOVIES", "entityName", "A different label"));
    assertThat(
            request(200, "GET", "/articles/" + a + "/entities", owner, null)
                .get("items")
                .get(0)
                .get("id")
                .asText())
        .isEqualTo(id);
    request(204, "DELETE", "/articles/" + a + "/entities/" + id, owner, null);
    request(204, "DELETE", "/articles/" + a + "/entities/" + id, owner, null);
    assertThat(request(200, "GET", "/articles/" + a + "/entities", owner, null).get("items").size())
        .isZero();
    assertThat(request(200, "GET", base + "/articles", owner, null).get("items").size())
        .isEqualTo(1);
    assertThat(request(200, "GET", base, owner, null).get("totalCompletionCount").asLong())
        .isEqualTo(2);
    assertThat(request(200, "GET", "/articles/" + a, owner, null).get("content").asText())
        .contains("Original first article text");
    assertThat(jdbc.queryForObject("select count(*) from articles", Integer.class)).isEqualTo(2);
    assertThat(jdbc.queryForObject("select count(*) from entities", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from entity_completions", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void everyTypeUsesItsOwnStatusesAndHistoryDeletionNeverRewritesStatus() throws Exception {
    for (var type : List.of("BOOK", "MOVIE", "TV_SHOW", "GAME")) {
      var initial =
          type.equals("BOOK") ? "TO_READ" : type.equals("GAME") ? "TO_PLAY" : "TO_BE_SEEN";
      var done = type.equals("BOOK") ? "READ" : type.equals("GAME") ? "FINISHED" : "SEEN";
      var entity =
          request(201, "POST", "/entities", owner, Map.of("type", type, "title", "Manual " + type));
      var base = "/entities/" + entity.get("id").asText();
      assertThat(entity.get("status").asText()).isEqualTo(initial);
      assertThat(request(200, "GET", base + "/articles", owner, null).get("items").size()).isZero();
      var invalid = request(400, "PATCH", base, owner, Map.of("version", 0, "status", "INVALID"));
      assertThat(invalid.get("reason").asText()).isEqualTo("INVALID_ENTITY_STATUS");
      var updated = request(200, "PATCH", base, owner, Map.of("version", 0, "status", done));
      assertThat(updated.get("lastCompletedAt").asText()).isEqualTo(NOW.toString());
      request(200, "PATCH", base, owner, Map.of("version", 1, "status", done));
      assertThat(request(200, "GET", base, owner, null).get("version").asLong()).isEqualTo(1);
      request(200, "PATCH", base, owner, Map.of("version", 1, "status", initial));
      var past =
          request(
              201,
              "POST",
              base + "/completions",
              owner,
              Map.of("version", 2, "completedAt", "2020-01-01T00:00:00Z"));
      assertThat(past.get("entity").get("status").asText()).isEqualTo(done);
      assertThat(past.get("entity").get("lastCompletedAt").asText()).isEqualTo(NOW.toString());
      for (var event : allItems(base + "/completions?limit=1", owner))
        request(
            204,
            "DELETE",
            base + "/completions/" + event.get("id").asText() + "?version=0",
            owner,
            null);
      var empty = request(200, "GET", base, owner, null);
      assertThat(empty.get("status").asText()).isEqualTo(done);
      assertThat(empty.get("totalCompletionCount").asLong()).isZero();
      assertThat(empty.get("lastCompletedAt").isNull()).isTrue();
    }
  }

  @Test
  void ownershipChecksCoverBothParentsAndNeverLeakForeignRecords() throws Exception {
    var article = article("owned");
    var entity =
        request(
            201,
            "POST",
            "/entities",
            owner,
            Map.of("type", "BOOK", "title", "Private", "status", "READ"));
    var id = entity.get("id").asText();
    var base = "/entities/" + id;
    var event =
        request(200, "GET", base + "/completions", owner, null)
            .get("items")
            .get(0)
            .get("id")
            .asText();
    for (var path :
        List.of(
            base, base + "/articles", base + "/completions", "/articles/" + article + "/entities"))
      assertThat(request(404, "GET", path, other, null).get("reason").asText())
          .isEqualTo("RESOURCE_NOT_FOUND");
    request(404, "PATCH", base, other, Map.of("version", 0, "title", "Stolen"));
    request(404, "POST", base + "/completions", other, Map.of("version", 0));
    request(
        404,
        "PATCH",
        base + "/completions/" + event,
        other,
        Map.of("version", 0, "completedAt", NOW.toString()));
    request(404, "DELETE", base + "/completions/" + event + "?version=0", other, null);
    request(404, "PUT", "/articles/" + article + "/entities/" + id, other, null);
    request(404, "DELETE", "/articles/" + article + "/entities/" + id, other, null);
    request(
        404,
        "POST",
        "/articles/" + article + "/entities",
        other,
        Map.of("type", "BOOK", "title", "Private"));
    var foreign =
        request(201, "POST", "/entities", other, Map.of("type", "BOOK", "title", "Other account"));
    request(
        404,
        "PUT",
        "/articles/" + article + "/entities/" + foreign.get("id").asText(),
        owner,
        null);
    request(
        404,
        "PATCH",
        base + "/completions/" + UUID.randomUUID(),
        owner,
        Map.of("version", 0, "completedAt", NOW.toString()));
    assertThat(request(200, "GET", base, owner, null).get("title").asText()).isEqualTo("Private");
    assertThat(request(200, "GET", base, owner, null).get("totalCompletionCount").asLong())
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from article_entities", Integer.class))
        .isZero();
  }

  @Test
  void linksAreManyToManyAndExplicitLinkIsIdempotentWithoutChangingEntityVersion()
      throws Exception {
    var a = article("a");
    var b = article("b");
    var book =
        request(201, "POST", "/entities", owner, Map.of("type", "BOOK", "title", "A book"))
            .get("id")
            .asText();
    var game =
        request(201, "POST", "/entities", owner, Map.of("type", "GAME", "title", "A game"))
            .get("id")
            .asText();
    var first = request(201, "PUT", "/articles/" + a + "/entities/" + book, owner, null);
    entityTestClock.set(NOW.plusSeconds(99));
    var repeat = request(200, "PUT", "/articles/" + a + "/entities/" + book, owner, null);
    assertThat(repeat.get("linkedAt").asText()).isEqualTo(first.get("linkedAt").asText());
    request(201, "PUT", "/articles/" + a + "/entities/" + game, owner, null);
    request(201, "PUT", "/articles/" + b + "/entities/" + book, owner, null);
    assertThat(allItems("/articles/" + a + "/entities?limit=1", owner)).hasSize(2);
    assertThat(allItems("/entities/" + book + "/articles?limit=1", owner)).hasSize(2);
    assertThat(request(200, "GET", "/entities/" + book, owner, null).get("version").asLong())
        .isZero();
    request(204, "DELETE", "/articles/" + a + "/entities/" + book, owner, null);
    assertThat(
            request(200, "GET", "/articles/" + a + "/entities", owner, null)
                .get("items")
                .get(0)
                .get("id")
                .asText())
        .isEqualTo(game);
    assertThat(
            request(200, "GET", "/entities/" + book + "/articles", owner, null)
                .get("items")
                .get(0)
                .get("id")
                .asText())
        .isEqualTo(b);
  }

  @Test
  void paginationLiteralSearchVersionsAndInvalidPayloadsDoNotCorruptRecords() throws Exception {
    var ids = new ArrayList<String>();
    for (int i = 0; i < 5; i++)
      ids.add(
          request(
                  201,
                  "POST",
                  "/entities",
                  owner,
                  Map.of("type", "BOOK", "title", "Literal 100%_ " + i))
              .get("id")
              .asText());
    request(
        201, "POST", "/entities", other, Map.of("type", "BOOK", "title", "Literal 100%_ private"));
    request(
        201, "POST", "/entities", owner, Map.of("type", "MOVIE", "title", "Literal 100%_ film"));
    var rows = allItems("/entities?type=BOOK&query=LITERAL%20100%25_&limit=2", owner);
    ids.sort(Comparator.reverseOrder());
    assertThat(rows.stream().map(e -> e.get("id").asText()).toList())
        .containsExactlyElementsOf(ids);
    var page = request(200, "GET", "/entities?type=BOOK&limit=1", owner, null);
    var cursor = page.get("nextCursor").asText();
    request(400, "GET", "/entities?type=MOVIE&cursor=" + cursor, owner, null);
    request(400, "GET", "/entities?type=BOOK&cursor=" + cursor, other, null);
    request(400, "GET", "/entities?type=BOOK&query=different&cursor=" + cursor, owner, null);
    for (var path :
        List.of(
            "/entities",
            "/entities?type=ALL",
            "/entities?type=BOOK&limit=0",
            "/entities?type=BOOK&limit=101",
            "/entities?type=BOOK&status=SEEN",
            "/entities?type=BOOK&completionYear=10000")) request(400, "GET", path, owner, null);
    var base = "/entities/" + ids.getFirst();
    request(400, "PATCH", base, owner, Map.of("version", 0.5, "title", "Invalid"));
    var nullTitle = new HashMap<String, Object>();
    nullTitle.put("version", 0);
    nullTitle.put("title", null);
    request(400, "PATCH", base, owner, nullTitle);
    request(400, "PATCH", base, owner, Map.of("version", 0, "type", "MOVIE", "title", "Invalid"));
    request(400, "POST", "/entities", owner, Map.of("type", "BOOK", "title", " "));
    request(400, "POST", "/entities", owner, Map.of("type", "BOOK", "title", "x".repeat(1001)));
    request(
        400,
        "POST",
        "/entities",
        owner,
        Map.of("type", "BOOK", "title", "Invalid status", "status", "SEEN"));
    request(
        400,
        "POST",
        "/entities",
        owner,
        Map.of("type", "BOOK", "title", "Bad time", "completedAt", NOW.toString()));
    request(
        400,
        "POST",
        "/entities",
        owner,
        Map.of(
            "type",
            "BOOK",
            "title",
            "Invalid range rolls back",
            "status",
            "READ",
            "completedAt",
            "+10000-01-01T00:00:00Z"));
    request(
        400,
        "PATCH",
        base,
        owner,
        Map.of("version", 0, "status", "READ", "completedAt", "+10000-01-01T00:00:00Z"));
    var duplicate =
        request(
            409,
            "PATCH",
            base,
            owner,
            Map.of("version", 0, "title", rows.getLast().get("title").asText(), "status", "READ"));
    assertThat(duplicate.get("reason").asText()).isEqualTo("DUPLICATE_ENTITY");
    assertThat(request(200, "GET", base, owner, null).get("version").asLong()).isZero();
    assertThat(jdbc.queryForObject("select count(*) from entity_completions", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select bool_and(title_hash=sha256(convert_to(title,'UTF8'))) from entities",
                Boolean.class))
        .isTrue();
  }

  @Test
  void concurrentManualCreationAndExplicitUpdatesHaveOneWinnerAndExactTitlesStayDistinct()
      throws Exception {
    var creates =
        race(
            () ->
                exchange(
                    "POST", "/entities", owner, Map.of("type", "BOOK", "title", "Concurrent")));
    assertThat(creates.stream().map(HttpResponse::statusCode).toList())
        .containsExactlyInAnyOrder(201, 409);
    var entity =
        json.readTree(
            creates.stream().filter(r -> r.statusCode() == 201).findFirst().orElseThrow().body());
    var base = "/entities/" + entity.get("id").asText();
    var updates =
        race(() -> exchange("PATCH", base, owner, Map.of("version", 0, "status", "READ")));
    assertThat(updates.stream().map(HttpResponse::statusCode).toList())
        .containsExactlyInAnyOrder(200, 409);
    assertThat(request(200, "GET", base, owner, null).get("totalCompletionCount").asLong())
        .isEqualTo(1);
    for (var title : List.of("concurrent", " Concurrent "))
      request(201, "POST", "/entities", owner, Map.of("type", "BOOK", "title", title));
    request(201, "POST", "/entities", owner, Map.of("type", "MOVIE", "title", "Concurrent"));
    request(201, "POST", "/entities", other, Map.of("type", "BOOK", "title", "Concurrent"));
    assertThat(jdbc.queryForObject("select count(*) from entities", Integer.class)).isEqualTo(5);
  }

  @Test
  void utcYearBoundariesRepeatedCompletionsAndBoundHistoryCursorsStayExact() throws Exception {
    var created =
        request(
            201,
            "POST",
            "/entities",
            owner,
            Map.of(
                "type",
                "MOVIE",
                "title",
                "Film 100%_",
                "status",
                "SEEN",
                "completedAt",
                "2026-01-01T00:30:00+01:00"));
    var id = created.get("id").asText();
    var base = "/entities/" + id;
    assertThat(created.get("version").asLong()).isZero();
    assertThat(created.get("lastCompletedAt").asText()).isEqualTo("2025-12-31T23:30:00Z");
    request(
        201,
        "POST",
        base + "/completions",
        owner,
        Map.of("version", 0, "completedAt", NOW.toString()));
    request(
        201,
        "POST",
        base + "/completions",
        owner,
        Map.of("version", 1, "completedAt", NOW.toString()));
    var history = allItems(base + "/completions?limit=1", owner);
    assertThat(history).hasSize(3);
    var tied = history.subList(0, 2).stream().map(e -> e.get("id").asText()).toList();
    assertThat(tied).isSortedAccordingTo(Comparator.reverseOrder());
    var years =
        request(
                200,
                "GET",
                "/entities/years?type=MOVIE&query=film%20100%25_&status=SEEN",
                owner,
                null)
            .get("years");
    assertThat(years.size()).isEqualTo(2);
    assertThat(years.get(0).get("completionCount").asLong()).isEqualTo(2);
    assertThat(years.get(1).get("year").asInt()).isEqualTo(2025);
    assertThat(
            request(200, "GET", "/entities?type=MOVIE&completionYear=2025", owner, null)
                .get("items")
                .get(0)
                .get("completionCount")
                .asLong())
        .isEqualTo(1);
    var otherEntity =
        request(201, "POST", "/entities", owner, Map.of("type", "MOVIE", "title", "Other film"))
            .get("id")
            .asText();
    var first = request(200, "GET", base + "/completions?limit=1", owner, null);
    request(
        400,
        "GET",
        "/entities/" + otherEntity + "/completions?cursor=" + first.get("nextCursor").asText(),
        owner,
        null);
    request(
        404,
        "PATCH",
        "/entities/" + otherEntity + "/completions/" + history.getFirst().get("id").asText(),
        owner,
        Map.of("version", 0, "completedAt", NOW.toString()));
    request(200, "PATCH", base, owner, Map.of("version", 2, "status", "TO_BE_SEEN"));
    assertThat(
            request(200, "GET", "/entities?type=MOVIE&completionYear=2025", owner, null)
                .get("items")
                .size())
        .isEqualTo(1);
    assertThat(
            request(200, "GET", "/entities/years?type=MOVIE&status=SEEN", owner, null)
                .get("years")
                .size())
        .isZero();
  }

  @Test
  void simultaneousArticleAddsReuseOneEntityAndDatabaseRejectsBrokenOwnershipOrHashes()
      throws Exception {
    var article = article("concurrent-add");
    var results =
        race(
            () ->
                exchange(
                    "POST",
                    "/articles/" + article + "/entities",
                    owner,
                    Map.of("type", "BOOK", "title", "One entity")));
    assertThat(results.stream().map(HttpResponse::statusCode).toList())
        .containsExactlyInAnyOrder(201, 200);
    assertThat(jdbc.queryForObject("select count(*) from entities", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from article_entities", Integer.class))
        .isEqualTo(1);
    var foreign =
        request(201, "POST", "/entities", other, Map.of("type", "BOOK", "title", "Foreign entity"))
            .get("id")
            .asText();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into article_entities(account_id,article_id,entity_id,linked_at) values (?,?,?,now())",
                    owner,
                    UUID.fromString(article),
                    UUID.fromString(foreign)))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update entities set title_hash=? where account_id=?", new byte[32], owner))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(
            request(200, "GET", "/articles/" + article + "/entities", owner, null)
                .get("items")
                .size())
        .isEqualTo(1);
  }

  private String article(String suffix) throws Exception {
    return request(
            201,
            "POST",
            "/articles",
            owner,
            Map.of(
                "url",
                "https://entity-flow.test/" + suffix,
                "title",
                "Source " + suffix,
                "content",
                "<p>Original " + suffix + " article text</p>"))
        .get("article")
        .get("id")
        .asText();
  }

  private List<JsonNode> allItems(String path, UUID account) throws Exception {
    var result = new ArrayList<JsonNode>();
    String next = path;
    do {
      var page = request(200, "GET", next, account, null);
      page.get("items").forEach(result::add);
      next =
          page.get("nextCursor").isNull()
              ? null
              : path + "&cursor=" + page.get("nextCursor").asText();
    } while (next != null);
    return result;
  }

  private JsonNode request(int status, String method, String path, UUID account, Object data)
      throws Exception {
    var response = exchange(method, path, account, data);
    assertThat(response.statusCode())
        .as(method + " " + path + " " + response.body())
        .isEqualTo(status);
    return json.readTree(response.body().isBlank() ? "null" : response.body());
  }

  private HttpResponse<String> exchange(String method, String path, UUID account, Object data)
      throws Exception {
    var request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:"
                        + environment.getProperty("local.server.port")
                        + "/api/v1"
                        + path))
            .header("Content-Type", "application/json")
            .header("X-Test-Account", account.toString())
            .method(
                method,
                data == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data)));
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private List<HttpResponse<String>> race(Callable<HttpResponse<String>> action) throws Exception {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var latch = new CountDownLatch(1);
      var first =
          executor.submit(
              () -> {
                latch.await();
                return action.call();
              });
      var second =
          executor.submit(
              () -> {
                latch.await();
                return action.call();
              });
      latch.countDown();
      return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
    }
  }
}
