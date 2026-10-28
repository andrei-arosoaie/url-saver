package com.know.screenshots.application;

import com.know.accounts.application.CurrentAccount;
import com.know.articles.application.ArticleQueries;
import com.know.screenshots.application.ScreenshotStorage.*;
import com.know.shared.application.RequestFailure;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Database operations are short transactions; never hold a connection during provider I/O. */
public class ScreenshotService {
  private final ArticleQueries articles;
  private final CurrentAccount account;
  private final ScreenshotStore store;
  private final ImageTransformation transformation;
  private final Map<String, ScreenshotStorage> providers;
  private final String activeProvider;
  private final Duration lifetime;
  private final Clock clock;

  public ScreenshotService(
      ArticleQueries articles,
      CurrentAccount account,
      ScreenshotStore store,
      ImageTransformation transformation,
      List<ScreenshotStorage> providers,
      String activeProvider,
      Duration lifetime,
      Clock clock) {
    this.articles = articles;
    this.account = account;
    this.store = store;
    this.transformation = transformation;
    this.activeProvider = activeProvider;
    this.lifetime = lifetime;
    this.clock = clock;
    var registered = new HashMap<String, ScreenshotStorage>();
    for (var provider : providers)
      if (registered.put(provider.provider(), provider) != null)
        throw new IllegalArgumentException("Duplicate screenshot provider.");
    this.providers = Map.copyOf(registered);
  }

  public View read(UUID article) {
    articles.detail(article);
    return view(store.read(account.requireId(), article));
  }

  public View upload(UUID article, UUID capture, long expectedVersion, byte[] input) {
    articles.detail(article);
    if (capture == null || expectedVersion < 0)
      throw new RequestFailure(
          400, "INVALID_REQUEST", "Supply a capture UUID and non-negative screenshot version.");
    var owner = account.requireId();
    var provider = provider(activeProvider);
    var claim =
        store.claim(
            owner, article, capture, expectedVersion, hash(input), activeProvider, clock.instant());
    if (claim.leaseId() == null) return view(claim.state());
    try {
      if (input == null) {
        store.complete(owner, article, capture, claim.leaseId(), null, null);
        return view(store.read(owner, article));
      }
      var image = transformation.transform(input);
      var planned = provider.planned(owner, claim.leaseId(), image.contentType());
      store.journal(claim.leaseId(), planned, clock.instant());
      var file = provider.upload(owner, claim.leaseId(), image);
      if (!store.complete(owner, article, capture, claim.leaseId(), file, image))
        throw new RequestFailure(
            409,
            "SCREENSHOT_VERSION_CONFLICT",
            "This capture was superseded by a newer screenshot.");
      return view(store.read(owner, article));
    } catch (RequestFailure failure) {
      store.failed(owner, article, claim.leaseId());
      throw failure;
    } catch (RuntimeException failure) {
      store.failed(owner, article, claim.leaseId());
      // Never expose raw SDK failures (signed URLs, provider request bodies, or keys).
      throw new RequestFailure(
          503,
          "SCREENSHOT_STORAGE_UNAVAILABLE",
          "Article saved; screenshot upload is not confirmed. Retry only the screenshot.");
    }
  }

  private View view(ScreenshotStore.State state) {
    if (state.file() == null)
      return new View("MISSING", state.version(), null, null, null, null, null, null);
    try {
      var signed = provider(state.file().provider()).sign(state.file(), lifetime);
      return new View(
          "AVAILABLE",
          state.version(),
          signed.url(),
          signed.expiresAt(),
          state.contentType(),
          state.byteSize(),
          state.width(),
          state.height());
    } catch (RuntimeException failure) {
      throw new RequestFailure(
          503,
          "SCREENSHOT_STORAGE_UNAVAILABLE",
          "Screenshot is temporarily unavailable. Refresh its URL later.");
    }
  }

  public void cleanup() {
    for (var item : store.due(clock.instant())) {
      try {
        provider(item.file().provider()).delete(item.file());
        if (item.file().id() != null || !clock.instant().isBefore(item.retainUntil()))
          store.cleaned(item.id());
        else store.postpone(item, clock.instant());
      } catch (RuntimeException failure) {
        store.postpone(item, clock.instant());
      }
    }
  }

  private ScreenshotStorage provider(String name) {
    var result = providers.get(name);
    if (result == null)
      throw new RequestFailure(
          503, "SCREENSHOT_STORAGE_UNAVAILABLE", "Screenshot storage adapter is unavailable.");
    return result;
  }

  private String hash(byte[] bytes) {
    if (bytes == null) return "missing";
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  public record View(
      String status,
      long version,
      String url,
      Instant expiresAt,
      String contentType,
      Long byteSize,
      Integer width,
      Integer height) {}
}
