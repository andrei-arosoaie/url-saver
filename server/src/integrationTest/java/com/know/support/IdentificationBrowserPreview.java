package com.know.support;

import com.know.KnowApplication;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.*;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

public class IdentificationBrowserPreview {
  static final String RESULT =
      "{\"sufficient\":true,\"category\":\"BOOKS\",\"subjects\":[{\"name\":\"1984\",\"main\":true},{\"name\":\"Animal Farm\",\"main\":false}]}";
  static volatile CountDownLatch held = new CountDownLatch(0);

  public static void main(String[] ignored) throws Exception {
    var db = new PostgreSQLContainer("postgres:17.11-alpine");
    db.start();
    var providers = new IdentificationProviderFixture();
    var app =
        SpringApplication.run(
            KnowApplication.class,
            "--spring.config.location=classpath:application-integration.yml",
            "--server.port=18096",
            "--spring.datasource.url=" + db.getJdbcUrl(),
            "--spring.datasource.username=" + db.getUsername(),
            "--spring.datasource.password=" + db.getPassword(),
            "--know.identification.scheduling-enabled=true",
            "--know.identification.request-timeout=30s",
            "--know.identification.providers.openrouter.base-url=" + providers.url("openrouter"),
            "--know.identification.providers.groq.base-url=" + providers.url("groq"));
    var jdbc = app.getBean(JdbcTemplate.class);
    var control = HttpServer.create(new InetSocketAddress("0.0.0.0", 18097), 0);
    control.createContext(
        "/",
        exchange -> {
          var action = exchange.getRequestURI().getPath();
          if (action.equals("/release")) held.countDown();
          else {
            providers.reset();
            jdbc.update("delete from identification_provider_health");
            if (action.equals("/success")) providers.result("openrouter", RESULT);
            if (action.equals("/fallback")) {
              providers.fail("openrouter", 503, "Unavailable");
              providers.result("groq", RESULT);
            }
            if (action.equals("/fail")) {
              providers.fail("openrouter", 503, "Unavailable");
              providers.fail("groq", 503, "Unavailable");
            }
            if (action.equals("/hold")) {
              held = new CountDownLatch(1);
              providers.result(
                  "openrouter",
                  RESULT,
                  () -> {
                    try {
                      held.await(25, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                    }
                  });
            }
          }
          var body =
              ("{\"openrouter\":"
                      + providers.openrouterRequests.size()
                      + ",\"groq\":"
                      + providers.groqRequests.size()
                      + "}")
                  .getBytes();
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    control.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    control.start();
    System.out.println("IDENTIFICATION_PREVIEW_READY: application 18096; fixture control 18097");
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  control.stop(0);
                  app.close();
                  providers.close();
                  db.stop();
                }));
    new CountDownLatch(1).await();
  }
}
