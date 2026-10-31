package com.know.connections.infrastructure.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.know.connections.application.*;
import java.io.StringWriter;
import java.time.Clock;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class ConnectionDiagnosticsTest {
  @Test
  void explainsDisabledLiveRequestsWithoutLoggingTheConfiguredKey() {
    var output = new StringWriter();
    var appender =
        WriterAppender.newBuilder()
            .setName("connection-diagnostics-test")
            .setTarget(output)
            .setLayout(PatternLayout.newBuilder().withPattern("%m%n").build())
            .build();
    var logger = (Logger) LogManager.getLogger(ConnectionWorker.class);
    var previous = logger.getLevel();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.INFO);
    var properties =
        new Binder(
                new MapConfigurationPropertySource(
                    Map.of(
                        "know.connections.enabled", "true",
                        "know.connections.embedding.gemini.api-key",
                            "private-key-must-not-be-logged")))
            .bindOrCreate("know.connections", ConnectionProperties.class);
    var worker =
        new ConnectionWorker(
            mock(ConnectionStore.class),
            mock(EmbeddingProvider.class),
            mock(ConnectionProfiles.class),
            properties,
            Clock.systemUTC());
    try {
      worker.configuration();
    } finally {
      worker.close();
      logger.removeAppender(appender);
      logger.setLevel(previous);
      appender.stop();
    }
    assertThat(output.toString())
        .contains(
            "enabled=true",
            "liveRequestsEnabled=false",
            "keyConfigured=true",
            "Semantic connections are paused",
            "know.connections.embedding.live-requests-enabled=false")
        .doesNotContain("private-key-must-not-be-logged");
  }
}
