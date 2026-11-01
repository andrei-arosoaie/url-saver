package com.know.support;

import com.know.KnowApplication;
import com.know.entities.application.*;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import org.springframework.boot.SpringApplication;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Disposable real application and local TV fixture. Never loads production secrets or TVmaze. */
public class TvBrowserPreview {
  public static void main(String[] ignored) throws Exception {
    var db =
        new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.7-pg17")
                .asCompatibleSubstituteFor("postgres"));
    db.start();
    var application =
        new SpringApplication(
            KnowApplication.class, BackendIntegrationSupport.AuthenticationFixture.class);
    var app =
        application.run(
            "--spring.config.location=classpath:application-integration.yml",
            "--server.port=18096",
            "--spring.datasource.url=" + db.getJdbcUrl(),
            "--spring.datasource.username=" + db.getUsername(),
            "--spring.datasource.password=" + db.getPassword(),
            "--know.identification.enabled=false",
            "--know.connections.enabled=false");
    var tv = app.getBean(TvService.class);
    var store = app.getBean(TvStore.class);
    var provider = app.getBean(TvProviderFixture.class);
    var clock = app.getBean(BackendIntegrationSupport.TestClock.class);
    var now = clock.instant();
    var first = tv.create(provider.show(1), null);
    var lease = store.claim(now, now.plusSeconds(120)).orElseThrow();
    store.publish(lease, provider.catalog(1), now);
    var progress = tv.position(first.id(), 0L, 1002, true);
    tv.markSeen(first.id(), progress.version(), progress.entityVersion());
    clock.set(Instant.parse("2026-10-26T00:01:00Z"));
    store.evaluateAirings(clock.instant());
    var second = tv.create(provider.show(2), "SEEING");
    now = clock.instant();
    lease = store.claim(now, now.plusSeconds(120)).orElseThrow();
    store.publish(lease, provider.catalog(2), now);
    tv.alias(second.id(), 0L, "The remake");
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  app.close();
                  db.stop();
                }));
    System.out.println("TV_PREVIEW_READY: http://localhost:18096; no live provider calls");
    new CountDownLatch(1).await();
  }
}
