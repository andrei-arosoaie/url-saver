package com.know.connections.application;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConnectionProfiles {
  private final ConnectionStore store;
  private final ConnectionPolicy policy;
  private final Clock clock;
  private volatile ConnectionModels.Profile current;

  public ConnectionModels.Profile current() {
    var existing = current;
    if (existing != null) return existing;
    synchronized (this) {
      if (current == null) current = store.profile(policy, clock.instant());
      return current;
    }
  }
}
