package com.know.screenshots.presentation;

import static org.assertj.core.api.Assertions.*;

import com.know.screenshots.application.ScreenshotService;
import com.know.support.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RequiredArgsConstructor(onConstructor_ = @Autowired)
class ScreenshotFlowsIT extends BackendIntegrationSupport {
  private final Environment environment;
  private final JdbcTemplate jdbc;
  private final ScreenshotProviderFixture provider;
  private final ScreenshotService service;
  private final HttpClient http = HttpClient.newHttpClient();
  private final JsonMapper json = JsonMapper.builder().build();
  private UUID owner;
  private UUID article;

  @BeforeEach
  void prepare() throws Exception {
    jdbc.execute(
        "truncate identification_jobs, identification_provider_health, screenshot_cleanup, article_screenshots, entity_completions,article_entities,entities,articles,categories,accounts");
    provider.reset();
    owner = UUID.randomUUID();
    jdbc.update(
        "insert into accounts(id,created_at) values (?,?)",
        owner,
        java.sql.Timestamp.from(Instant.now()));
    var created =
        send(
            "POST",
            "/api/v1/articles",
            owner,
            json.writeValueAsString(
                Map.of(
                    "url",
                    "https://example.test/screenshot-" + UUID.randomUUID(),
                    "title",
                    "Original article",
                    "content",
                    "<p>Original article text survives.</p>")));
    assertThat(created.statusCode()).isEqualTo(201);
    article = UUID.fromString(json.readTree(created.body()).get("article").get("id").asText());
  }

  @Test
  void saveUploadTransformPrivateSignReadReplaceAndCleanupIsAnEndToEndFlow() throws Exception {
    assertThat(body(read(owner)).get("status").asText()).isEqualTo("MISSING");
    var png = png(1024, 768);
    assertThat(png.length).isGreaterThan(1024 * 1024);
    var capture = UUID.randomUUID();
    var uploaded = upload(owner, capture, 0, png);
    assertThat(uploaded.statusCode()).as(uploaded.body()).isEqualTo(200);
    var result = body(uploaded);
    assertThat(result.get("status").asText()).isEqualTo("AVAILABLE");
    assertThat(result.get("width").asInt()).isEqualTo(1024);
    assertThat(result.get("height").asInt()).isEqualTo(768);
    assertThat(result.get("contentType").asText()).isEqualTo("image/jpeg");
    assertThat(Instant.parse(result.get("expiresAt").asText()))
        .isBetween(Instant.now().plusSeconds(1700), Instant.now().plusSeconds(1900));
    assertThat(uploaded.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    assertThat(provider.files.values()).allMatch(ScreenshotProviderFixture.File::privateFile);
    var delivery =
        http.send(
            HttpRequest.newBuilder(URI.create(result.get("url").asText())).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray());
    assertThat(delivery.statusCode()).isEqualTo(200);
    assertThat(ImageIO.read(new ByteArrayInputStream(delivery.body())).getWidth()).isEqualTo(1024);
    assertThat(provider.uploads.get()).isEqualTo(1);
    var repeated = body(read(owner));
    assertThat(repeated.get("url").asText()).isEqualTo(result.get("url").asText());
    assertThat(repeated.get("expiresAt").asText()).isEqualTo(result.get("expiresAt").asText());
    assertThat(provider.signatures.get()).isEqualTo(1);
    var foreign = UUID.randomUUID();
    jdbc.update(
        "insert into accounts(id,created_at) values (?,?)",
        foreign,
        java.sql.Timestamp.from(Instant.now()));
    assertThat(read(foreign).statusCode()).isEqualTo(404);
    assertThat(provider.signatures.get()).isEqualTo(1);
    assertThat(
            send("GET", "/api/v1/articles/" + article + "/screenshot?refresh=true", owner, null)
                .statusCode())
        .isEqualTo(200);
    assertThat(provider.signatures.get()).isEqualTo(2);
    assertThat(upload(owner, capture, 0, png).statusCode()).isEqualTo(200);
    assertThat(provider.uploads.get()).isEqualTo(1);
    assertThat(upload(owner, capture, 0, png(20, 10)).statusCode()).isEqualTo(409);
    assertThat(upload(owner, UUID.randomUUID(), 0, png).statusCode()).isEqualTo(409);
    assertThat(upload(owner, UUID.randomUUID(), 1, png(30, 20)).statusCode()).isEqualTo(200);
    assertThat(body(read(owner)).get("url").asText()).isNotEqualTo(result.get("url").asText());
    assertThat(provider.files).hasSize(2);
    service.cleanup();
    assertThat(provider.files).hasSize(1);
    assertThat(jdbc.queryForObject("select count(*) from screenshot_cleanup", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject("select content from articles where id=?", String.class, article))
        .isEqualTo("<p>Original article text survives.</p>");
    assertThat(jdbc.queryForObject("select version from articles where id=?", Long.class, article))
        .isZero();
  }

  @Test
  void ownershipFailuresDoNotLeakOrUploadAndCaptureFailureShowsMissingWithoutLosingArticle()
      throws Exception {
    var foreign = UUID.randomUUID();
    jdbc.update(
        "insert into accounts(id,created_at) values (?,?)",
        foreign,
        java.sql.Timestamp.from(Instant.now()));
    assertThat(read(foreign).statusCode()).isEqualTo(404);
    assertThat(upload(foreign, UUID.randomUUID(), 0, png(10, 10)).statusCode()).isEqualTo(404);
    assertThat(provider.uploads.get()).isZero();
    var first = upload(owner, UUID.randomUUID(), 0, png(10, 10));
    assertThat(first.statusCode()).isEqualTo(200);
    var missing = upload(owner, UUID.randomUUID(), 1, null);
    assertThat(missing.statusCode()).isEqualTo(200);
    assertThat(body(missing).get("status").asText()).isEqualTo("MISSING");
    service.cleanup();
    assertThat(provider.files).isEmpty();
    assertThat(jdbc.queryForObject("select count(*) from articles", Integer.class)).isEqualTo(1);
  }

  @Test
  void providerFailureAndLostResponseRetainArticleAllowRetryAndCleanUnknownOrphans()
      throws Exception {
    var capture = UUID.randomUUID();
    var png = png(50, 40);
    provider.commitThenFail = true;
    var uncertain = upload(owner, capture, 0, png);
    assertThat(uncertain.statusCode()).as(uncertain.body()).isEqualTo(503);
    assertThat(body(read(owner)).get("status").asText()).isEqualTo("MISSING");
    assertThat(jdbc.queryForObject("select count(*) from articles", Integer.class)).isEqualTo(1);
    assertThat(provider.files).hasSize(1);
    var retry = upload(owner, capture, 0, png);
    assertThat(retry.statusCode()).as(retry.body()).isEqualTo(200);
    assertThat(provider.files).hasSize(2);
    jdbc.execute(
        "update screenshot_cleanup set next_attempt_at=now()-interval '1 minute',retain_until=now()-interval '1 minute'");
    service.cleanup();
    assertThat(provider.files).hasSize(1);
    assertThat(body(read(owner)).get("status").asText()).isEqualTo("AVAILABLE");
  }

  @Test
  void permanentArticleDeletionRemovesMetadataAndDurablyRetriesRemoteCleanup() throws Exception {
    assertThat(upload(owner, UUID.randomUUID(), 0, png(30, 20)).statusCode()).isEqualTo(200);
    assertThat(provider.files).hasSize(1);
    var deleted = send("DELETE", "/api/v1/articles/" + article + "?version=0", owner, null);
    assertThat(deleted.statusCode()).as(deleted.body()).isEqualTo(204);
    assertThat(read(owner).statusCode()).isEqualTo(404);
    assertThat(jdbc.queryForObject("select count(*) from article_screenshots", Integer.class))
        .isZero();
    assertThat(jdbc.queryForObject("select count(*) from screenshot_cleanup", Integer.class))
        .isEqualTo(1);
    provider.failDelete = true;
    service.cleanup();
    assertThat(provider.files).hasSize(1);
    assertThat(jdbc.queryForObject("select attempts from screenshot_cleanup", Integer.class))
        .isEqualTo(1);
    provider.failDelete = false;
    jdbc.execute("update screenshot_cleanup set next_attempt_at=now()-interval '1 minute'");
    service.cleanup();
    assertThat(provider.files).isEmpty();
    assertThat(jdbc.queryForObject("select count(*) from screenshot_cleanup", Integer.class))
        .isZero();
    assertThat(send("GET", "/api/v1/articles/" + article, owner, null).statusCode()).isEqualTo(404);
  }

  @Test
  void deletionDuringUploadCannotRecreateArticleAndLateFileIsCleaned() throws Exception {
    provider.uploadStored = new java.util.concurrent.CountDownLatch(1);
    provider.continueUpload = new java.util.concurrent.CountDownLatch(1);
    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var uploading = executor.submit(() -> upload(owner, UUID.randomUUID(), 0, png(30, 20)));
      try {
        assertThat(provider.uploadStored.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        var deleted = send("DELETE", "/api/v1/articles/" + article + "?version=0", owner, null);
        assertThat(deleted.statusCode()).as(deleted.body()).isEqualTo(204);
      } finally {
        provider.continueUpload.countDown();
      }
      assertThat(uploading.get(5, java.util.concurrent.TimeUnit.SECONDS).statusCode())
          .isEqualTo(409);
    }
    assertThat(provider.files).hasSize(1);
    assertThat(read(owner).statusCode()).isEqualTo(404);
    assertThat(jdbc.queryForObject("select count(*) from article_screenshots", Integer.class))
        .isZero();
    jdbc.execute(
        "update screenshot_cleanup set next_attempt_at=now()-interval '1 minute',retain_until=now()-interval '1 minute'");
    service.cleanup();
    assertThat(provider.files).isEmpty();
    assertThat(jdbc.queryForObject("select count(*) from screenshot_cleanup", Integer.class))
        .isZero();
  }

  @Test
  void invalidImageIsRejectedWithMissingPlaceholderAndTheArticleStillReadable() throws Exception {
    var response = upload(owner, UUID.randomUUID(), 0, new byte[] {1, 2, 3});
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(provider.uploads.get()).isZero();
    assertThat(body(read(owner)).get("status").asText()).isEqualTo("MISSING");
    assertThat(send("GET", "/api/v1/articles/" + article, owner, null).statusCode()).isEqualTo(200);
  }

  private byte[] png(int width, int height) throws Exception {
    var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    var random = new Random(4);
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) image.setRGB(x, y, random.nextInt(0x1000000));
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(image, "png", bytes);
    return bytes.toByteArray();
  }

  private HttpResponse<String> read(UUID account) throws Exception {
    return send("GET", "/api/v1/articles/" + article + "/screenshot", account, null);
  }

  private JsonNode body(HttpResponse<String> response) {
    return json.readTree(response.body());
  }

  private HttpResponse<String> send(String method, String path, UUID account, String body)
      throws Exception {
    var request = HttpRequest.newBuilder(uri(path)).header("X-Test-Account", account.toString());
    if (body != null) request.header("Content-Type", "application/json");
    return http.send(
        request
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + environment.getProperty("local.server.port") + path);
  }

  private HttpResponse<String> upload(UUID account, UUID capture, long version, byte[] png)
      throws Exception {
    var bytes = new ByteArrayOutputStream();
    String boundary = "know-test-boundary";
    bytes.write(
        ("--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\"metadata\"\r\nContent-Type: application/json\r\n\r\n"
                + json.writeValueAsString(Map.of("captureId", capture, "expectedVersion", version))
                + "\r\n")
            .getBytes(StandardCharsets.UTF_8));
    if (png != null) {
      bytes.write(
          ("--"
                  + boundary
                  + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"capture.png\"\r\nContent-Type: image/png\r\n\r\n")
              .getBytes(StandardCharsets.UTF_8));
      bytes.write(png);
      bytes.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
    bytes.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return http.send(
        HttpRequest.newBuilder(uri("/api/v1/articles/" + article + "/screenshot"))
            .header("X-Test-Account", account.toString())
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray()))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
