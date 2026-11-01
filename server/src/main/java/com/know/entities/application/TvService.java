package com.know.entities.application;

import static com.know.entities.application.TvModels.*;

import com.know.accounts.application.CurrentAccount;
import com.know.shared.application.RequestFailure;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TvService {
  private final CurrentAccount current;
  private final EntityStore entities;
  private final TvStore store;
  private final TvPolicy policy;
  private final EntitySettings settings;
  private final Clock entityClock;

  @Transactional
  public EntityModels.Details create(Show show, String status) {
    var owner = current.requireId();
    entities.lockWrites(owner);
    duplicate(owner, show.tvmazeId(), null);
    EntityType.TV_SHOW.validateStatus(status == null ? "TO_BE_SEEN" : status);
    // Insert using provider identity from the start: distinct remakes may have identical names.
    var created =
        entities.createIdentified(owner, show, status == null ? "TO_BE_SEEN" : status, now());
    store.enqueue(show.tvmazeId(), now());
    return created;
  }

  @Transactional
  public Details link(UUID id, Long entityVersion, Show show) {
    var owner = owner();
    var entity = entity(owner, id);
    version(entityVersion, entity.version());
    var previous = store.mapping(owner, id);
    if (previous.isPresent()) {
      if (previous.get().tvmazeId() != show.tvmazeId())
        throw failure(409, "TV_SHOW_MAPPING_CONFLICT");
      return details(owner, id);
    }
    duplicate(owner, show.tvmazeId(), id);
    store.link(owner, id, show, now());
    if (entity.status().equals("SEEN")) store.seen(owner, id, now());
    entities.revise(owner, id, show.name(), entity.status(), true);
    store.enqueue(show.tvmazeId(), now());
    return details(owner, id);
  }

  @Transactional
  public Details read(UUID id) {
    var owner = owner();
    entity(owner, id);
    var mapping = mapping(owner, id);
    var meta = store.metadata(mapping.tvmazeId());
    if (meta.fetchedAt() == null || meta.fetchedAt().isBefore(now().minus(policy.catalogTtl())))
      store.enqueue(mapping.tvmazeId(), now());
    return details(owner, id);
  }

  public Seasons seasons(UUID id) {
    var owner = current.requireId();
    entity(owner, id);
    var m = mapping(owner, id);
    return new Seasons(store.seasons(owner, m, now()), store.metadata(m.tvmazeId()).state());
  }

  public Episodes episodes(UUID id, long seasonId) {
    var owner = current.requireId();
    entity(owner, id);
    var m = mapping(owner, id);
    if (store.seasons(owner, m, now()).stream().noneMatch(s -> s.id() == seasonId)) throw missing();
    return new Episodes(
        store.episodes(owner, m, now()).stream()
            .filter(e -> Objects.equals(e.seasonId(), seasonId))
            .toList(),
        store.metadata(m.tvmazeId()).state());
  }

  @Transactional
  public Details alias(UUID id, Long version, String value) {
    var owner = owner();
    entity(owner, id);
    var m = mapping(owner, id);
    version(version, m.version());
    if (value != null && value.codePointCount(0, value.length()) > settings.maxTitleCodePoints())
      throw failure(400, "INVALID_REQUEST");
    var alias = value == null || value.isBlank() ? null : value;
    if (!Objects.equals(alias, m.alias())) {
      store.alias(owner, id, alias, now());
      touch(owner, id);
    }
    return details(owner, id);
  }

  @Transactional
  public Details position(UUID id, Long version, long episodeId, boolean bulk) {
    var owner = owner();
    var entity = entity(owner, id);
    var m = lockedMapping(owner, id);
    version(version, m.version());
    var episodes = store.episodes(owner, m, now());
    var selected = episode(episodes, episodeId);
    if (!aired(selected, now())) throw failure(409, "EPISODE_NOT_AIRED");
    boolean changed = !Objects.equals(m.lastWatchedEpisodeId(), episodeId);
    if (changed) store.position(owner, id, episodeId, now());
    if (bulk)
      for (var e : episodes)
        if (aired(e, now()) && compare(e, selected) <= 0)
          changed |= store.watch(owner, id, e.id(), true, now());
    boolean statusChanged = seeing(owner, entity);
    if (changed || statusChanged) {
      store.bump(owner, id, now());
      if (!statusChanged) touch(owner, id);
    }
    if (bulk) clearIfCaughtUp(owner, id);
    return details(owner, id);
  }

  @Transactional
  public Details watch(UUID id, Long version, long episodeId, boolean watched) {
    var owner = owner();
    var entity = entity(owner, id);
    var m = lockedMapping(owner, id);
    version(version, m.version());
    var e = episode(store.episodes(owner, m, now()), episodeId);
    if (watched && !e.canMarkWatched()) throw failure(409, "EPISODE_NOT_AIRED");
    boolean changed = store.watch(owner, id, episodeId, watched, now());
    boolean statusChanged = watched && seeing(owner, entity);
    if (changed || statusChanged) {
      store.bump(owner, id, now());
      if (!statusChanged) touch(owner, id);
    }
    if (watched) clearIfCaughtUp(owner, id);
    return details(owner, id);
  }

  @Transactional
  public Details markSeen(UUID id, Long version, Long entityVersion) {
    var owner = owner();
    var entity = entity(owner, id);
    var m = lockedMapping(owner, id);
    version(version, m.version());
    version(entityVersion, entity.version());
    var details = details(owner, id);
    if (!details.canMarkSeen()) throw failure(409, "SHOW_NOT_CAUGHT_UP");
    if (!entity.status().equals("SEEN")) {
      entities.addCompletion(owner, id, now(), now());
      entities.revise(owner, id, entity.title(), "SEEN", true);
      store.seen(owner, id, now());
    }
    return details(owner, id);
  }

  @Transactional
  public Details replace(UUID id, Long version, Long entityVersion, Show show, boolean confirmed) {
    if (!confirmed) throw failure(400, "CONFIRMATION_REQUIRED");
    var owner = owner();
    var entity = entity(owner, id);
    var m = lockedMapping(owner, id);
    version(version, m.version());
    version(entityVersion, entity.version());
    duplicate(owner, show.tvmazeId(), id);
    if (m.tvmazeId() == show.tvmazeId()) return details(owner, id);
    store.replace(owner, id, show, now());
    entities.revise(owner, id, show.name(), "TO_BE_SEEN", true);
    store.enqueue(show.tvmazeId(), now());
    return details(owner, id);
  }

  private Details details(UUID owner, UUID id) {
    var e = entity(owner, id);
    var m = mapping(owner, id);
    var meta = store.metadata(m.tvmazeId());
    var episodes = store.episodes(owner, m, now());
    long aired = episodes.stream().filter(x -> aired(x, now())).count();
    long watched = episodes.stream().filter(Episode::watched).count();
    boolean caughtUp =
        meta.fetchedAt() != null
            && aired > 0
            && episodes.stream().filter(x -> aired(x, now())).allMatch(Episode::watched);
    return new Details(
        id,
        m.tvmazeId(),
        m.version(),
        e.version(),
        e.title(),
        m.alias(),
        e.status(),
        store.show(m.tvmazeId()),
        m.lastWatchedEpisodeId(),
        watched,
        aired,
        m.newEpisodesAvailable(),
        caughtUp,
        meta);
  }

  private void clearIfCaughtUp(UUID owner, UUID id) {
    if (details(owner, id).canMarkSeen() && mapping(owner, id).newEpisodesAvailable()) {
      store.clearNew(owner, id, now());
      touch(owner, id);
    }
  }

  private boolean seeing(UUID owner, EntityModels.Details e) {
    if (!e.status().equals("TO_BE_SEEN")) return false;
    entities.revise(owner, e.id(), e.title(), "SEEING", false);
    return true;
  }

  private void touch(UUID owner, UUID id) {
    var e = entity(owner, id);
    entities.revise(owner, id, e.title(), e.status(), true);
  }

  private UUID owner() {
    var owner = current.requireId();
    entities.lockWrites(owner);
    return owner;
  }

  private EntityModels.Details entity(UUID owner, UUID id) {
    var e = entities.find(owner, id).orElseThrow(TvService::missing);
    if (e.type() != EntityType.TV_SHOW) throw failure(400, "INVALID_ENTITY_TYPE");
    return e;
  }

  private Mapping lockedMapping(UUID owner, UUID id) {
    var mapping = mapping(owner, id);
    store.lockCatalog(mapping.tvmazeId());
    return mapping;
  }

  private Mapping mapping(UUID owner, UUID id) {
    return store.mapping(owner, id).orElseThrow(TvService::missing);
  }

  private void duplicate(UUID owner, long show, UUID except) {
    store
        .mappedEntity(owner, show)
        .filter(id -> !id.equals(except))
        .ifPresent(
            id -> {
              throw new RequestFailure(
                  409, "DUPLICATE_ENTITY", "This show is already in your entities.", id);
            });
  }

  private Instant now() {
    return entityClock.instant();
  }

  private static Episode episode(List<Episode> episodes, long id) {
    return episodes.stream().filter(e -> e.id() == id).findFirst().orElseThrow(TvService::missing);
  }

  public static boolean aired(Episode e, Instant now) {
    return e.airAt() != null && !e.airAt().isAfter(now);
  }

  private static int compare(Episode a, Episode b) {
    int time = a.airAt().compareTo(b.airAt());
    return time != 0
        ? time
        : Comparator.comparingInt(Episode::season)
            .thenComparing(e -> e.number() == null ? Integer.MAX_VALUE : e.number())
            .thenComparingLong(Episode::id)
            .compare(a, b);
  }

  private static void version(Long expected, long actual) {
    if (expected == null || expected < 0) throw failure(400, "INVALID_REQUEST");
    if (expected != actual) throw failure(409, "VERSION_CONFLICT");
  }

  private static RequestFailure missing() {
    return failure(404, "RESOURCE_NOT_FOUND");
  }

  private static RequestFailure failure(int status, String reason) {
    return new RequestFailure(status, reason, reason.replace('_', ' '));
  }
}
