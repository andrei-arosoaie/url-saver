package com.know.identification.infrastructure.config;

import com.know.identification.application.IdentificationWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IdentificationScheduling {
  private final IdentificationProperties properties;
  private final IdentificationWorker worker;

  @Scheduled(
      fixedDelayString = "${know.identification.poll-interval:2s}",
      initialDelayString = "${know.identification.poll-interval:2s}")
  public void poll() {
    if (properties.enabled() && properties.schedulingEnabled()) worker.runOne();
  }
}
