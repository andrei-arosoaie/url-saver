package com.know.entities.application;

import static com.know.entities.application.TvModels.*;

import java.time.Instant;
import java.util.*;

public interface TvStore {
  Optional<Mapping> mapping(UUID owner, UUID entity);

  void lockCatalog(long show);

  Optional<UUID> mappedEntity(UUID owner, long show);

  void link(UUID owner, UUID entity, Show show, Instant now);

  void replace(UUID owner, UUID entity, Show show, Instant now);

  void alias(UUID owner, UUID entity, String alias, Instant now);

  void position(UUID owner, UUID entity, long episode, Instant now);

  boolean watch(UUID owner, UUID entity, long episode, boolean watched, Instant now);

  void seen(UUID owner, UUID entity, Instant now);

  void clearNew(UUID owner, UUID entity, Instant now);

  void bump(UUID owner, UUID entity, Instant now);

  Show show(long id);

  Metadata metadata(long id);

  List<Season> seasons(UUID owner, Mapping mapping, Instant now);

  List<Episode> episodes(UUID owner, Mapping mapping, Instant now);

  void enqueue(long show, Instant now);

  void enqueueDue(Instant now, Instant staleBefore);

  Optional<Lease> claim(Instant now, Instant until);

  void publish(Lease lease, Catalog catalog, Instant now);

  void failed(Lease lease, String reason, Instant retryAt);

  void evaluateAirings(Instant now);

  Optional<CachedSearch> cached(String key);

  boolean claimSearch(String key, String query, UUID token, Instant now, Instant until);

  void cache(String key, UUID token, List<Candidate> items, Instant now, Instant expires);

  void failSearch(String key, UUID token, Instant retryAt);

  void cleanup(Instant before, int limit);

  boolean claimProvider(UUID token, Instant now, Instant until);

  void releaseProvider(UUID token, Instant blockedUntil);
}
