package com.know.screenshots.infrastructure.config;

import com.know.screenshots.application.ScreenshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "know.screenshots.cleanup.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ScreenshotCleanup {
  private final ScreenshotService service;

  @Scheduled(
      fixedDelayString = "${know.screenshots.cleanup-delay:60000}",
      initialDelayString = "${know.screenshots.cleanup-delay:60000}")
  public void run() {
    service.cleanup();
  }
}
