package com.know.screenshots.application;

import com.know.screenshots.application.ScreenshotStorage.*;
import java.time.Clock;
import java.util.*;
import java.util.function.Supplier;

/**
 * Cache delivery URLs only. Ownership and current screenshot state are checked before every lookup.
 */
public class ScreenshotUrlCache {
  public record Key(UUID account, long version, StoredFile file) {}

  private final Map<Key, Download> urls = new LinkedHashMap<>(16, 0.75f, true);
  private final int capacity;
  private final Clock clock;

  public ScreenshotUrlCache(int capacity, Clock clock) {
    if (capacity < 1)
      throw new IllegalArgumentException("Screenshot URL cache capacity must be positive.");
    this.capacity = capacity;
    this.clock = clock;
  }

  public synchronized Download get(Key key, boolean refresh, Supplier<Download> sign) {
    var cached = urls.get(key);
    if (!refresh && cached != null && cached.expiresAt().isAfter(clock.instant().plusSeconds(5)))
      return cached;
    urls.remove(key);
    var generated = sign.get();
    urls.put(key, generated);
    while (urls.size() > capacity) urls.remove(urls.keySet().iterator().next());
    return generated;
  }
}
