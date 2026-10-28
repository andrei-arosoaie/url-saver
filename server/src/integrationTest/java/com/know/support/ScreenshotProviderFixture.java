package com.know.support;

import com.know.screenshots.application.ScreenshotStorage;
import com.know.screenshots.infrastructure.storage.ImageKitStorage;
import com.sun.net.httpserver.HttpServer;
import io.imagekit.client.okhttp.ImageKitOkHttpClient;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.databind.json.JsonMapper;

/** Test-only HTTP provider. Real official SDK, real multipart, no external account or keys. */
public class ScreenshotProviderFixture implements AutoCloseable {
  public record File(String id, String path, byte[] bytes, boolean privateFile) {}

  public final Map<String, File> files = new ConcurrentHashMap<>();
  public final AtomicInteger uploads = new AtomicInteger();
  public volatile boolean failUpload;
  public volatile boolean commitThenFail;
  private final HttpServer server;
  private final JsonMapper json = JsonMapper.builder().build();
  private final String origin;

  public ScreenshotProviderFixture() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    origin = "http://127.0.0.1:" + server.getAddress().getPort();
    server.createContext(
        "/",
        exchange -> {
          try {
            var path = exchange.getRequestURI().getPath();
            if (exchange.getRequestMethod().equals("POST") && path.equals("/api/v1/files/upload")) {
              uploads.incrementAndGet();
              if (failUpload) {
                respond(exchange, 503, "{\"message\":\"test provider unavailable\"}");
                return;
              }
              String type = exchange.getRequestHeaders().getFirst("Content-Type");
              String boundary = type.substring(type.indexOf("boundary=") + 9).replace("\"", "");
              var fields = new HashMap<String, String>();
              String raw =
                  new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
              for (var part : raw.split(java.util.regex.Pattern.quote("--" + boundary))) {
                int divider = part.indexOf("\r\n\r\n");
                if (divider < 0) continue;
                var matcher =
                    java.util.regex.Pattern.compile("name=\"([^\"]+)\"")
                        .matcher(part.substring(0, divider));
                if (matcher.find()) {
                  var value = part.substring(divider + 4);
                  if (value.endsWith("\r\n")) value = value.substring(0, value.length() - 2);
                  fields.put(matcher.group(1), value);
                }
              }
              var id = UUID.randomUUID().toString();
              var file =
                  new File(
                      id,
                      fields.get("folder") + "/" + fields.get("fileName"),
                      fields.get("file").getBytes(StandardCharsets.ISO_8859_1),
                      "true".equals(fields.get("isPrivateFile")));
              if (!file.privateFile()) {
                respond(exchange, 400, "{\"message\":\"private upload required\"}");
                return;
              }
              files.put(id, file);
              if (commitThenFail) {
                commitThenFail = false;
                respond(exchange, 503, "{\"message\":\"response lost after upload\"}");
                return;
              }
              respond(
                  exchange,
                  200,
                  json.writeValueAsString(
                      Map.of("fileId", id, "filePath", file.path(), "url", origin + file.path())));
              return;
            }
            if (exchange.getRequestMethod().equals("DELETE") && path.startsWith("/v1/files/")) {
              files.remove(path.substring("/v1/files/".length()));
              exchange.sendResponseHeaders(204, -1);
              exchange.close();
              return;
            }
            if (exchange.getRequestMethod().equals("GET") && path.equals("/v1/files")) {
              respond(
                  exchange,
                  200,
                  json.writeValueAsString(
                      files.values().stream()
                          .map(
                              f ->
                                  Map.of(
                                      "type",
                                      "file",
                                      "fileId",
                                      f.id(),
                                      "filePath",
                                      f.path(),
                                      "name",
                                      f.path().substring(f.path().lastIndexOf('/') + 1)))
                          .toList()));
              return;
            }
            for (var file : files.values())
              if (path.equals(file.path())) {
                if (exchange.getRequestURI().getQuery() == null
                    || !exchange.getRequestURI().getQuery().contains("ik-s=")) {
                  respond(exchange, 401, "{}");
                  return;
                }
                exchange
                    .getResponseHeaders()
                    .set("Content-Type", path.endsWith(".png") ? "image/png" : "image/jpeg");
                exchange.sendResponseHeaders(200, file.bytes().length);
                exchange.getResponseBody().write(file.bytes());
                exchange.close();
                return;
              }
            respond(exchange, 404, "{}");
          } catch (Exception error) {
            respond(exchange, 500, "{\"message\":\"test provider protocol mismatch\"}");
          }
        });
    server.start();
  }

  private static void respond(com.sun.net.httpserver.HttpExchange exchange, int code, String text)
      throws IOException {
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(code, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  public ScreenshotStorage storage() {
    return new ImageKitStorage(
        ImageKitOkHttpClient.builder()
            .privateKey("test-only-private-key")
            .baseUrl(origin)
            .timeout(Duration.ofSeconds(2))
            .maxRetries(0)
            .build(),
        origin,
        Clock.systemUTC()) {
      @Override
      public String provider() {
        return "test";
      }
    };
  }

  public void reset() {
    files.clear();
    uploads.set(0);
    failUpload = false;
    commitThenFail = false;
  }

  public void close() {
    server.stop(0);
  }
}
