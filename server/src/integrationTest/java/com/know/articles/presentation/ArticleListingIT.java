package com.know.articles.presentation;

import static org.assertj.core.api.Assertions.*;

import com.know.support.BackendIntegrationSupport;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor(onConstructor_ = @Autowired)
class ArticleListingIT extends BackendIntegrationSupport {
  private final JdbcTemplate jdbc;
  private final Environment environment;
  private final HttpClient http = HttpClient.newHttpClient();
  private final JsonMapper json = JsonMapper.builder().build();
  private UUID owner;
  private UUID other;

  @BeforeEach
  void fixtures() {
    jdbc.execute(
        "truncate screenshot_cleanup, article_screenshots, entity_completions, article_entities, entities, articles, categories, accounts");
    owner = UUID.randomUUID();
    other = UUID.randomUUID();
    for (var account : List.of(owner, other))
      jdbc.update(
          "insert into accounts(id,created_at) values (?,?)",
          account,
          java.sql.Timestamp.from(Instant.now()));
  }

  @Test
  void saveListLoadMoreAndReadEveryArticleWithoutDuplicatesOrForeignContent() throws Exception {
    var expected = new ArrayList<String>();
    var tiedTime = java.sql.Timestamp.from(Instant.parse("2026-10-24T10:00:00Z"));
    for (int i = 0; i < 5; i++) {
      var article = save(owner, "https://example.test/" + i, "<p>Article " + i + "</p>");
      var id = article.get("id").asText();
      expected.add(id);
      jdbc.update("update articles set saved_at=? where id=?", tiedTime, UUID.fromString(id));
    }
    save(other, "https://private.test/secret", "private content");
    expected.sort(Comparator.reverseOrder());
    var first = body(get(owner, "?limit=2"));
    var seen = ids(first);
    assertThat(seen).containsExactlyElementsOf(expected.subList(0, 2));
    for (var item : first.get("items")) {
      assertThat(item.has("content")).isFalse();
      assertThat(item.has("accountId")).isFalse();
      assertThat(item.get("hasContent").asBoolean()).isTrue();
    }
    var newlySaved = save(owner, "https://example.test/new", "<p>New</p>");
    var cursor = first.get("nextCursor");
    while (!cursor.isNull()) {
      var page = body(get(owner, "?limit=2&cursor=" + cursor.asText()));
      seen.addAll(ids(page));
      cursor = page.get("nextCursor");
    }
    assertThat(seen).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
    assertThat(seen).doesNotContain(newlySaved.get("id").asText());
    for (var id : seen) {
      var article = body(get(owner, "/" + id));
      assertThat(article.get("id").asText()).isEqualTo(id);
      assertThat(article.get("content").asText()).startsWith("<p>Article ");
      assertThat(get(other, "/" + id).statusCode()).isEqualTo(404);
    }
    assertThat(body(get(owner, "?limit=1")).get("items").get(0).get("id"))
        .isEqualTo(newlySaved.get("id"));
    var foreignCursor = first.get("nextCursor").asText();
    assertThat(ids(body(get(other, "?limit=2&cursor=" + foreignCursor))))
        .doesNotContainAnyElementsOf(expected);
  }

  @Test
  void linkOnlyAppearsInListAndCanBeReadThenEnrichedWithoutChangingIdentityOrOrder()
      throws Exception {
    var empty = body(get(owner, ""));
    assertThat(empty.get("items").size()).isZero();
    assertThat(empty.get("nextCursor").isNull()).isTrue();
    var response =
        send("POST", "", owner, Map.of("url", "https://example.test/link", "title", "Link"));
    assertThat(response.statusCode()).isEqualTo(201);
    var saved = body(response).get("article");
    var id = saved.get("id").asText();
    var first = body(get(owner, ""));
    assertThat(first.get("items").get(0).get("hasContent").asBoolean()).isFalse();
    assertThat(first.get("nextCursor").isNull()).isTrue();
    assertThat(body(get(owner, "/" + id)).get("content").isNull()).isTrue();
    var enriched =
        send(
            "POST",
            "",
            owner,
            Map.of(
                "url",
                "https://example.test/link",
                "title",
                "Link",
                "content",
                "<p>Now readable</p>"));
    assertThat(enriched.statusCode()).isEqualTo(200);
    var item = body(get(owner, "")).get("items").get(0);
    assertThat(item.get("id").asText()).isEqualTo(id);
    assertThat(item.get("hasContent").asBoolean()).isTrue();
    assertThat(item.get("version").asLong()).isEqualTo(1);
    assertThat(item.get("savedAt")).isEqualTo(saved.get("savedAt"));
    assertThat(body(get(owner, "/" + id)).get("content").asText()).isEqualTo("<p>Now readable</p>");
  }

  @Test
  void defaultsToTwentyAndAcceptsMaximumLimitWithoutCountOrContentFields() throws Exception {
    for (int i = 0; i < 21; i++) save(owner, "https://example.test/default/" + i, "body");
    var first = body(get(owner, ""));
    assertThat(first.get("items").size()).isEqualTo(20);
    assertThat(first.has("totalCount")).isFalse();
    var next = body(get(owner, "?cursor=" + first.get("nextCursor").asText()));
    assertThat(next.get("items").size()).isEqualTo(1);
    assertThat(next.get("nextCursor").isNull()).isTrue();
    var all = body(get(owner, "?limit=100"));
    assertThat(all.get("items").size()).isEqualTo(21);
    assertThat(all.get("nextCursor").isNull()).isTrue();
    var expected = body(get(owner, "/" + first.get("items").get(0).get("id").asText()));
    assertThat(expected.get("content").asText()).isEqualTo("body");
  }

  @Test
  void rejectsInvalidLimitsAndBookmarksAndKeepsTemporaryAccountIsolated() throws Exception {
    for (var query :
        List.of(
            "?limit=0",
            "?limit=-1",
            "?limit=101",
            "?limit=abc",
            "?limit=1.5",
            "?cursor=",
            "?cursor=not-a-cursor",
            "?cursor=" + "a".repeat(257))) {
      var response = get(owner, query);
      assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
      assertThat(body(response).get("reason").asText()).isEqualTo("INVALID_REQUEST");
    }
    var temporary = get(null, "");
    assertThat(temporary.statusCode()).isEqualTo(200);
    assertThat(json.readTree(temporary.body()).get("items").size()).isZero();
    assertThat(jdbc.queryForObject("select count(*) from articles", Integer.class)).isZero();
  }

  private JsonNode save(UUID account, String url, String content) throws Exception {
    var response =
        send("POST", "", account, Map.of("url", url, "title", "Article", "content", content));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return body(response).get("article");
  }

  private HttpResponse<String> get(UUID account, String path) throws Exception {
    return send("GET", path, account, null);
  }

  private HttpResponse<String> send(String method, String path, UUID account, Object payload)
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
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)));
    if (account != null) request.header("X-Test-Account", account.toString());
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode body(HttpResponse<String> response) {
    return json.readTree(response.body());
  }

  private ArrayList<String> ids(JsonNode page) {
    var ids = new ArrayList<String>();
    for (var item : page.get("items")) ids.add(item.get("id").asText());
    return ids;
  }
}
