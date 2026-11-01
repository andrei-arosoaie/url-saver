package com.know.entities.application;

import static com.know.entities.application.TvModels.*;

import com.know.accounts.application.CurrentAccount;
import com.know.shared.application.RequestFailure;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Each store reservation commits before provider I/O. No user state enters this public cache. */
@Service
@RequiredArgsConstructor
public class TvSearch {
  private final CurrentAccount current;
  private final TvStore store;
  private final TvProvider provider;
  private final TvPolicy policy;
  private final Clock entityClock;

  public Search search(String query) {
    current.requireId();
    String normalized =
        query == null ? "" : query.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    if (normalized.length() < policy.minimumCharacters() || normalized.length() > 200)
      throw new RequestFailure(
          400,
          "INVALID_REQUEST",
          "Enter between " + policy.minimumCharacters() + " and 200 characters.");
    String key = "tvmaze:shows:v1:" + normalized;
    var now = entityClock.instant();
    var cached = store.cached(key);
    if (cached.isPresent() && cached.get().expiresAt().isAfter(now))
      return result(cached.get().items(), normalized, "FRESH");
    var token = UUID.randomUUID();
    if (store.claimSearch(key, normalized, token, now, now.plus(policy.leaseDuration()))) {
      try {
        var items = provider.search(normalized);
        var finished = entityClock.instant();
        store.cache(
            key,
            token,
            items,
            finished,
            finished.plus(items.isEmpty() ? policy.emptyTtl() : policy.positiveTtl()));
        return result(items, normalized, "FRESH");
      } catch (RuntimeException failure) {
        store.failSearch(key, token, entityClock.instant().plus(policy.retryDelay()));
        if (cached.isEmpty() || cached.get().expiresAt().plus(policy.staleWindow()).isBefore(now))
          throw failure;
        return result(cached.get().items(), normalized, "STALE_UNAVAILABLE");
      }
    }
    // Re-read after losing the claim: another pod may have published while we were claiming.
    cached = store.cached(key);
    if (cached.isPresent() && !cached.get().expiresAt().plus(policy.staleWindow()).isBefore(now))
      return result(
          cached.get().items(),
          normalized,
          cached.get().expiresAt().isAfter(now) ? "FRESH" : "STALE_REFRESHING");
    return new Search(
        List.of(),
        "NEEDS_SELECTION",
        "PENDING",
        policy.minimumCharacters(),
        policy.debounceMs(),
        policy.pageSize());
  }

  private Search result(List<Candidate> items, String query, String state) {
    long exact = items.stream().filter(i -> i.name().equalsIgnoreCase(query)).count();
    return new Search(
        items,
        items.isEmpty() ? "NO_MATCH" : exact == 1 ? "UNAMBIGUOUS" : "NEEDS_SELECTION",
        state,
        policy.minimumCharacters(),
        policy.debounceMs(),
        policy.pageSize());
  }
}
