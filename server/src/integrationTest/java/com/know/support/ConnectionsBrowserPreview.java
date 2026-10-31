package com.know.support;

import com.know.KnowApplication;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;
import org.springframework.boot.SpringApplication;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

/** Disposable real backend + local Google protocol fixture. Never loads user secrets. */
public class ConnectionsBrowserPreview {
  private static volatile String mode = "success";
  private static volatile CountDownLatch held = new CountDownLatch(0);

  public static void main(String[] ignored) throws Exception {
    var json = JsonMapper.builder().build();
    var db =
        new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.7-pg17")
                .asCompatibleSubstituteFor("postgres"));
    db.start();
    var fixture = HttpServer.create(new InetSocketAddress("127.0.0.1", 18098), 0);
    fixture.createContext(
        "/",
        exchange -> {
          var request =
              json.readTree(
                  new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          boolean counting = exchange.getRequestURI().getPath().endsWith(":countTokens");
          int status = 200;
          Object result;
          if (counting) {
            String text =
                request.path("contents").get(0).path("parts").get(0).path("text").asText();
            result = Map.of("totalTokens", Math.max(1, text.length() / 3));
          } else {
            try {
              held.await(25, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            if (mode.equals("fail") || mode.equals("quota")) {
              status = mode.equals("quota") ? 429 : 503;
              result = Map.of("error", "Controlled provider response");
            } else {
              float[] vector = new float[request.path("outputDimensionality").asInt(128)];
              vector[0] = 1;
              result = Map.of("embedding", Map.of("values", vector));
            }
          }
          byte[] bytes = json.writeValueAsString(result).getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    fixture.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    fixture.start();
    var app =
        SpringApplication.run(
            KnowApplication.class,
            "--spring.config.location=classpath:application-integration.yml",
            "--server.port=18096",
            "--spring.datasource.url=" + db.getJdbcUrl(),
            "--spring.datasource.username=" + db.getUsername(),
            "--spring.datasource.password=" + db.getPassword(),
            "--know.identification.enabled=false",
            "--know.connections.scheduling-enabled=true",
            "--know.connections.poll-interval=250ms",
            "--know.connections.ui-poll-interval=500ms",
            "--know.connections.provider-cooldown=2s",
            "--know.connections.retry-delays=1s,1s,1s,1s,1s",
            "--know.connections.backfill-interval=1d",
            "--know.connections.embedding.gemini.base-url=http://127.0.0.1:18098");
    var control = HttpServer.create(new InetSocketAddress("0.0.0.0", 18097), 0);
    control.createContext(
        "/",
        exchange -> {
          String action = exchange.getRequestURI().getPath().substring(1);
          if (action.equals("release")) {
            mode = "success";
            held.countDown();
          } else {
            mode = action;
            held.countDown();
            held = action.equals("hold") ? new CountDownLatch(1) : new CountDownLatch(0);
          }
          byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    control.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    control.start();
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  held.countDown();
                  control.stop(0);
                  fixture.stop(0);
                  app.close();
                  db.stop();
                }));
    System.out.println(
        "CONNECTIONS_PREVIEW_READY: application 18096; control 18097; no live providers");
    new CountDownLatch(1).await();
  }
}
