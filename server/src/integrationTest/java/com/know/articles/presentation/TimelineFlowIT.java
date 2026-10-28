package com.know.articles.presentation;

import static org.assertj.core.api.Assertions.*;

import com.know.support.BackendIntegrationSupport;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor(onConstructor_ = @Autowired)
class TimelineFlowIT extends BackendIntegrationSupport {
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
      jdbc.update("insert into accounts(id,created_at) values (?,now())", account);
  }

  @Test
  void saveCategorizeSearchPaginateAndClearMetadataWithoutLosingContent() throws Exception {
    var catalog = send("GET", "/categories", owner, null);
    assertThat(catalog.statusCode()).as(catalog.body()).isEqualTo(200);
    assertThat(body(catalog).get("items").size()).isEqualTo(8);
    var ai =
        java.util.stream.StreamSupport.stream(body(catalog).get("items").spliterator(), false)
            .filter(category -> category.get("key").asText().equals("AI"))
            .findFirst()
            .orElseThrow();
    assertThat(ai.get("color").asText()).isEqualTo("#0891b2");
    assertThat(body(catalog).get("items").get(0).get("isDefault").asBoolean()).isTrue();
    var ids = new ArrayList<String>();
    for (int i = 0; i < 3; i++) {
      var saved =
          body(send(
                  "POST",
                  "/articles",
                  owner,
                  Map.of(
                      "url",
                      "https://metadata.test/" + i,
                      "title",
                      "Article",
                      "content",
                      "<p>Original text</p>")))
              .get("article");
      var id = saved.get("id").asText();
      ids.add(id);
      assertThat(saved.get("category").asText()).isEqualTo("OTHER");
      var updated =
          send(
              "PATCH",
              "/articles/" + id,
              owner,
              Map.of("version", 0, "category", "BOOKS", "entityName", "Exact 100%_ Name"));
      assertThat(updated.statusCode()).as(updated.body()).isEqualTo(200);
      assertThat(body(updated).get("version").asLong()).isEqualTo(1);
    }
    var page = send("GET", "/articles?category=BOOKS&query=100%25_&limit=2", owner, null);
    assertThat(page.statusCode()).as(page.body()).isEqualTo(200);
    var first = body(page);
    assertThat(first.get("items").size()).isEqualTo(2);
    var cursor = first.get("nextCursor").asText();
    var next =
        body(
            send(
                "GET",
                "/articles?category=BOOKS&query=100%25_&limit=2&cursor=" + cursor,
                owner,
                null));
    assertThat(next.get("items").size()).isEqualTo(1);
    assertThat(next.get("nextCursor").isNull()).isTrue();
    assertThat(
            send("GET", "/articles?category=MOVIES&query=100%25_&cursor=" + cursor, owner, null)
                .statusCode())
        .isEqualTo(400);
    assertThat(
            send("GET", "/articles?category=BOOKS&query=100%25_&cursor=" + cursor, other, null)
                .statusCode())
        .isEqualTo(400);
    assertThat(body(send("GET", "/articles?query=unmatched", owner, null)).get("items").size())
        .isZero();
    var id = ids.getFirst();
    assertThat(
            send("PATCH", "/articles/" + id, owner, Map.of("version", 1, "category", "UNKNOWN"))
                .statusCode())
        .isEqualTo(400);
    var clearing = new HashMap<String, Object>();
    clearing.put("version", 1);
    clearing.put("entityName", null);
    var cleared = send("PATCH", "/articles/" + id, owner, clearing);
    assertThat(cleared.statusCode()).as(cleared.body()).isEqualTo(200);
    assertThat(body(cleared).get("entityName").isNull()).isTrue();
    assertThat(body(cleared).get("content").asText()).contains("Original text");
    assertThat(body(cleared).get("category").asText()).isEqualTo("BOOKS");
    assertThat(body(cleared).get("version").asLong()).isEqualTo(2);
    assertThat(
            send("PATCH", "/articles/" + id, owner, Map.of("version", 2, "category", "AI"))
                .statusCode())
        .isEqualTo(200);
    assertThat(body(send("GET", "/articles?category=AI", owner, null)).get("items").size())
        .isEqualTo(1);
    assertThat(
            send("PATCH", "/articles/" + id, other, Map.of("version", 2, "category", "MOVIES"))
                .statusCode())
        .isEqualTo(404);
    assertThat(
            jdbc.queryForObject(
                "select identified_entity_name from articles where id=?",
                String.class,
                UUID.fromString(id)))
        .isNull();
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
            .header("Content-Type", "application/json")
            .header("X-Test-Account", account.toString())
            .method(
                method,
                payload == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)));
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode body(HttpResponse<String> response) {
    return json.readTree(response.body());
  }
}
