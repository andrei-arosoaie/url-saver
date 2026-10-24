package com.know.articles.presentation;

import static org.assertj.core.api.Assertions.*;

import com.know.support.BackendIntegrationSupport;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor(onConstructor_ = @Autowired)
class OpenApiContractIT extends BackendIntegrationSupport {
  private final Environment environment;
  private final HttpClient http = HttpClient.newHttpClient();
  private final JsonMapper json = JsonMapper.builder().build();

  @Test
  void publishesAndExportsTheFourRealArticleContractsAsDeterministicYaml() throws Exception {
    var response = get("/v3/api-docs");
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    var document = json.readTree(response.body());
    assertThat(document.get("servers").get(0).get("url").asText()).isEqualTo("/");
    var paths = document.get("paths");
    assertThat(paths.size()).isEqualTo(2);
    var collection = paths.get("/api/v1/articles");
    var detail = paths.get("/api/v1/articles/{id}");
    assertThat(collection.has("get")).isTrue();
    assertThat(collection.has("post")).isTrue();
    var limitSchema = collection.get("get").get("parameters").get(0).get("schema");
    assertThat(limitSchema.get("type").asText()).isEqualTo("integer");
    assertThat(limitSchema.get("default").isIntegralNumber()).isTrue();
    assertThat(limitSchema.get("default").asInt()).isEqualTo(20);
    assertThat(collection.get("post").get("responses").get("201").get("headers").has("Location"))
        .isTrue();
    assertThat(detail.has("get")).isTrue();
    assertThat(detail.has("patch")).isTrue();
    assertThat(collection.get("post").get("responses").has("201")).isTrue();
    assertThat(collection.get("post").get("responses").has("409")).isTrue();
    assertThat(detail.get("patch").get("responses").has("409")).isTrue();
    assertThat(detail.get("get").get("responses").has("404")).isTrue();
    var schemas = document.get("components").get("schemas");
    var update = schemas.get("UpdateArticleRequest");
    assertThat(update.get("minProperties").asInt()).isEqualTo(2);
    assertThat(update.get("required").toString())
        .contains("version")
        .doesNotContain("title", "content");
    assertThat(update.get("properties").has("content")).isTrue();
    assertThat(update.get("properties").has("url")).isFalse();
    var summary = schemas.get("ArticleSummaryResponse").get("properties");
    assertThat(summary.has("content")).isFalse();
    assertThat(summary.has("accountId")).isFalse();
    assertThat(summary.has("hasContent")).isTrue();
    assertThat(summary.has("version")).isTrue();
    assertThat(
            schemas.get("ArticleResponse").get("properties").get("content").get("type").toString())
        .contains("\"string\"", "\"null\"");
    assertThat(
            schemas
                .get("ArticlePageResponse")
                .get("properties")
                .get("nextCursor")
                .get("type")
                .toString())
        .contains("\"string\"", "\"null\"");
    assertThat(schemas.get("ApiErrorResponse").get("properties").has("reason")).isTrue();
    assertThat(schemas.get("ApiErrorResponse").get("properties").has("existingArticleId")).isTrue();
    var yaml = get("/v3/api-docs.yaml");
    assertThat(yaml.statusCode()).as(yaml.body()).isEqualTo(200);
    var normalized = yaml.body().replace("\r\n", "\n").stripTrailing() + "\n";
    assertThat(normalized)
        .contains("openapi:", "/api/v1/articles:", "operationId: listArticles")
        .doesNotContain("localhost:", "X-Test-Account");
    assertThat(get("/v3/api-docs.yaml").body().replace("\r\n", "\n").stripTrailing() + "\n")
        .isEqualTo(normalized);
    var exported = Path.of("build/openapi/openapi.yaml");
    Files.createDirectories(exported.getParent());
    Files.writeString(exported, normalized, StandardCharsets.UTF_8);
    var ui = get("/swagger-ui/index.html");
    assertThat(ui.statusCode()).isEqualTo(200);
  }

  private HttpResponse<String> get(String path) throws Exception {
    return http.send(
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:" + environment.getProperty("local.server.port") + path))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
