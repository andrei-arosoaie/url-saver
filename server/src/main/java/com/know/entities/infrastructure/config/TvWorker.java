package com.know.entities.infrastructure.config;

import com.know.entities.application.*;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "know.tvmaze.scheduling-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class TvWorker {
  private final TvStore store;
  private final TvProvider provider;
  private final TvPolicy policy;
  private final Clock entityClock;

  @Scheduled(fixedDelayString = "${know.tvmaze.poll-interval:10000}")
  public void poll() {
    var now = entityClock.instant();
    store.enqueueDue(now, now.minus(policy.catalogTtl()));
    store
        .claim(now, now.plus(policy.leaseDuration()))
        .ifPresent(
            lease -> {
              try {
                store.publish(lease, provider.catalog(lease.showId()), entityClock.instant());
              } catch (RuntimeException failure) {
                store.failed(
                    lease, "TVMAZE_UNAVAILABLE", entityClock.instant().plus(policy.retryDelay()));
              }
            });
    store.evaluateAirings(entityClock.instant());
    store.cleanup(
        now.minus(policy.staleWindow()).minus(policy.positiveTtl()), policy.cacheRowLimit());
  }
}
