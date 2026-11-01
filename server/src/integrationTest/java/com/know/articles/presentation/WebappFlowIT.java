package com.know.articles.presentation;

import static org.assertj.core.api.Assertions.*;

import com.know.support.BackendIntegrationSupport;
import java.net.URI;
import java.net.http.*;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor(onConstructor_ = @Autowired)
class WebappFlowIT extends BackendIntegrationSupport {
  private final JdbcTemplate jdbc;
  private final Environment environment;
  private final HttpClient http = HttpClient.newHttpClient();
  private final JsonMapper json = JsonMapper.builder().build();

  @BeforeEach
  void reset() {
    jdbc.execute(
        "truncate identity_refresh_tokens,identity_codes,identity_sessions,external_account_providers,entity_episode_watches,entity_tv_shows,tvmaze_refresh_jobs,tvmaze_episodes,tvmaze_seasons,tvmaze_shows,tvmaze_search_cache,embedding_provider_permits,embedding_provider_cooldowns,connection_shared_entities,article_connections,article_connection_exclusions,article_embeddings,article_connection_jobs,identification_jobs, identification_provider_health, screenshot_cleanup, article_screenshots, entity_completions, article_entities, entities, articles, categories, accounts");
  }

  @Test
  void servesWebappAndAssetsAlongsideTheCompleteSavedArticleReadingFlow() throws Exception {
    var saved =
        http.send(
            HttpRequest.newBuilder(uri("/api/v1/articles"))
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        json.writeValueAsString(
                            Map.of(
                                "url",
                                "https://example.test/webapp-flow",
                                "title",
                                "A saved article",
                                "content",
                                "<p>Every word is preserved.</p><script>evil()</script>"))))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(saved.statusCode()).isEqualTo(201);
    var id = json.readTree(saved.body()).get("article").get("id").asText();
    var index = get("/");
    assertThat(index.statusCode()).isEqualTo(200);
    assertThat(index.headers().firstValue("Content-Type").orElse("")).contains("text/html");
    assertThat(index.body()).contains("Know", "id=\"root\"");
    var assets = Pattern.compile("(?:src|href)=\"(/assets/[^\"]+)\"").matcher(index.body());
    int count = 0;
    while (assets.find()) {
      assertThat(get(assets.group(1)).statusCode()).isEqualTo(200);
      count++;
    }
    assertThat(count).isGreaterThanOrEqualTo(2);
    var list = json.readTree(get("/api/v1/articles?limit=20").body());
    assertThat(list.get("items").get(0).get("id").asText()).isEqualTo(id);
    var detail = json.readTree(get("/api/v1/articles/" + id).body());
    assertThat(detail.get("content").asText()).isEqualTo("<p>Every word is preserved.</p>");
    assertThat(jdbc.queryForObject("select count(*) from articles", Integer.class)).isEqualTo(1);
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + environment.getProperty("local.server.port") + path);
  }

  private HttpResponse<String> get(String path) throws Exception {
    return http.send(
        HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
  }
}
