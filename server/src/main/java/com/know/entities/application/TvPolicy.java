package com.know.entities.application;

import java.time.Duration;

public interface TvPolicy {
  int minimumCharacters();

  int debounceMs();

  int pageSize();

  int cacheRowLimit();

  Duration catalogTtl();

  Duration positiveTtl();

  Duration emptyTtl();

  Duration staleWindow();

  Duration leaseDuration();

  Duration retryDelay();
}
