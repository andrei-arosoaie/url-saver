package com.know.screenshots.application;

import static org.assertj.core.api.Assertions.*;

import com.know.screenshots.application.ScreenshotStorage.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ScreenshotUrlCacheTest {
  static class Time extends Clock {
    Instant value = Instant.parse("2026-10-30T00:00:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return value;
    }
  }

  private final Time clock = new Time();
  private final AtomicInteger signatures = new AtomicInteger();
  private final Supplier<Download> sign =
      () ->
          new Download(
              "https://images.test/" + signatures.incrementAndGet(),
              clock.instant().plusSeconds(1800));
  private final StoredFile file = new StoredFile("test", "file", "/file.jpg");

  @Test
  void reusesUntilExpiryMarginAndAllowsAnExplicitRefreshWithoutExtendingCachedExpiry() {
    var cache = new ScreenshotUrlCache(10, clock);
    var key = new ScreenshotUrlCache.Key(UUID.randomUUID(), 1, file);
    var first = cache.get(key, false, sign);
    clock.value = clock.value.plusSeconds(100);
    assertThat(cache.get(key, false, sign)).isEqualTo(first);
    assertThat(signatures.get()).isEqualTo(1);
    var refreshed = cache.get(key, true, sign);
    assertThat(refreshed.url()).isNotEqualTo(first.url());
    clock.value = refreshed.expiresAt().minusSeconds(5);
    assertThat(cache.get(key, false, sign).url()).isNotEqualTo(refreshed.url());
    assertThat(signatures.get()).isEqualTo(3);
  }

  @Test
  void separatesAccountsFilesAndVersionsAndBoundsMemoryWithLeastRecentlyUsedEviction() {
    var cache = new ScreenshotUrlCache(2, clock);
    var owner = UUID.randomUUID();
    var first = new ScreenshotUrlCache.Key(owner, 1, file);
    var foreign = new ScreenshotUrlCache.Key(UUID.randomUUID(), 1, file);
    var original = cache.get(first, false, sign);
    assertThat(cache.get(foreign, false, sign).url()).isNotEqualTo(original.url());
    assertThat(cache.get(first, false, sign)).isEqualTo(original);
    cache.get(
        new ScreenshotUrlCache.Key(owner, 2, new StoredFile("test", "replacement", "/new.jpg")),
        false,
        sign);
    assertThat(cache.get(first, false, sign)).isEqualTo(original);
    cache.get(foreign, false, sign);
    assertThat(signatures.get()).isEqualTo(4);
  }

  @Test
  void neverCachesSigningFailures() {
    var cache = new ScreenshotUrlCache(2, clock);
    var key = new ScreenshotUrlCache.Key(UUID.randomUUID(), 1, file);
    assertThatThrownBy(
            () ->
                cache.get(
                    key,
                    false,
                    () -> {
                      throw new IllegalStateException("unavailable");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(cache.get(key, false, sign).url()).isNotBlank();
  }
}
