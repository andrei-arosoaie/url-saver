package com.know.entities.infrastructure.persistence;

import static com.know.entities.application.EntityModels.*;

import com.know.entities.application.*;
import com.know.shared.application.RequestFailure;
import com.know.shared.application.ScopedCursor.Position;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaEntityStore implements EntityStore {
  private final EntityRepository entities;
  private final CompletionRepository events;
  private final LinkRepository links;
  private final EntityPersistenceMapper mapper;
  private final EntityManager em;
  private final java.time.Clock entityClock;

  public boolean identified(UUID owner, UUID id) {
    return ((Number)
                em.createNativeQuery(
                        "select count(*) from entity_tv_shows where account_id=:owner and"
                            + " entity_id=:id",
                        Long.class)
                    .setParameter("owner", owner)
                    .setParameter("id", id)
                    .getSingleResult())
            .longValue()
        > 0;
  }

  public Details createIdentified(UUID owner, TvModels.Show show, String status, Instant now) {
    em.createNativeQuery(
            "insert into tvmaze_shows(tvmaze_id,name) values (:show,:name) on conflict do nothing")
        .setParameter("show", show.tvmazeId())
        .setParameter("name", show.name())
        .executeUpdate();
    var entity =
        new TrackedEntity(owner, EntityType.TV_SHOW, show.name(), hash(show.name()), status, now);
    entity.identify(show.tvmazeId());
    entities.saveAndFlush(entity);
    em.createNativeQuery(
            "insert into entity_tv_shows(account_id,entity_id,show_id,created_at,updated_at) values"
                + " (:owner,:id,:show,:now,:now)")
        .setParameter("owner", owner)
        .setParameter("id", entity.getId())
        .setParameter("show", show.tvmazeId())
        .setParameter("now", now)
        .executeUpdate();
    if (status.equals("SEEN")) {
      addCompletion(owner, entity.getId(), now, now);
      em.createNativeQuery(
              "update entity_tv_shows set last_marked_seen_at=:now where account_id=:owner and"
                  + " entity_id=:id")
          .setParameter("now", now)
          .setParameter("owner", owner)
          .setParameter("id", entity.getId())
          .executeUpdate();
    }
    return detail(owner, entity);
  }

  public void lockWrites(UUID owner) {
    // Serialize an account's entity mutations across server instances. The DB releases this lock
    // on commit/rollback; unique constraints and versions remain independent final safeguards.
    em.createNativeQuery(
            "select 1 from pg_advisory_xact_lock(hashtextextended(:key,0))", Integer.class)
        .setParameter("key", "know-entities:" + owner)
        .getSingleResult();
  }

  public Optional<Details> find(UUID owner, UUID id) {
    return entities.findByIdAndAccountId(id, owner).map(e -> detail(owner, e));
  }

  public Optional<Details> findExact(UUID owner, EntityType type, String title) {
    return entities
        .findByAccountIdAndTypeAndTitleHash(owner, type, hash(title))
        .map(
            e -> {
              if (!e.getTitle().equals(title))
                throw new RequestFailure(
                    500, "ENTITY_TITLE_HASH_COLLISION", "Unable to identify the entity safely.");
              return detail(owner, e);
            });
  }

  public Details insert(UUID owner, Create command, Instant now) {
    return detail(
        owner,
        entities.saveAndFlush(
            new TrackedEntity(
                owner,
                command.type(),
                command.title(),
                hash(command.title()),
                command.status(),
                now)));
  }

  public Details revise(UUID owner, UUID id, String title, String status, boolean historyChanged) {
    var entity = entities.findByIdAndAccountId(id, owner).orElseThrow();
    if (!entity.getStatus().equals(status)) {
      em.createNativeQuery(
              "update entity_tv_shows set"
                  + " version=version+1,updated_at=:now,last_marked_seen_at=case when"
                  + " :status='SEEN' then :now else last_marked_seen_at end where account_id=:owner"
                  + " and entity_id=:id")
          .setParameter("now", entityClock.instant())
          .setParameter("status", status)
          .setParameter("owner", owner)
          .setParameter("id", id)
          .executeUpdate();
    }
    boolean changed = !entity.getTitle().equals(title) || !entity.getStatus().equals(status);
    if (changed) entity.revise(title, hash(title), status);
    else if (historyChanged) em.lock(entity, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    entities.flush();
    return detail(owner, entity);
  }

  public List<Summary> list(UUID owner, Filter filter, Position before, int limit) {
    var cb = em.getCriteriaBuilder();
    var query = cb.createQuery(TrackedEntity.class);
    var root = query.from(TrackedEntity.class);
    var predicates = filters(cb, root, owner, filter);
    if (filter.completionYear() != null) {
      var sub = query.subquery(UUID.class);
      var c = sub.from(CompletionEntity.class);
      var range = range(filter.completionYear());
      sub.select(c.get("entityId"))
          .where(
              cb.equal(c.get("accountId"), owner),
              cb.equal(c.get("entityId"), root.get("id")),
              cb.greaterThanOrEqualTo(c.get("completedAt"), range[0]),
              cb.lessThan(c.get("completedAt"), range[1]));
      predicates.add(cb.exists(sub));
    }
    before(cb, root, predicates, "createdAt", before);
    query
        .select(root)
        .where(predicates.toArray(Predicate[]::new))
        .orderBy(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));
    var rows = em.createQuery(query).setMaxResults(limit).getResultList();
    var ids = rows.stream().map(TrackedEntity::getId).toList();
    var all = stats(owner, ids, null);
    var matching =
        filter.completionYear() == null ? all : stats(owner, ids, filter.completionYear());
    return rows.stream()
        .map(
            e -> {
              var total = all.getOrDefault(e.getId(), Stats.EMPTY);
              var match = matching.getOrDefault(e.getId(), Stats.EMPTY);
              return mapper.summary(
                  mapper.details(e, total.last, total.count), match.count, match.last);
            })
        .toList();
  }

  public List<Year> years(UUID owner, Filter filter) {
    // Explicit AT TIME ZONE makes grouping independent of the database session timezone.
    var sql =
        new StringBuilder(
            "select extract(year from c.completed_at at time zone 'UTC')::integer, count(distinct"
                + " e.id), count(*) from entity_completions c join entities e on e.id=c.entity_id"
                + " and e.account_id=c.account_id where e.account_id=:owner and e.type=:type");
    if (filter.status() != null) sql.append(" and e.status=:status");
    if (filter.query() != null)
      sql.append(
          " and (lower(e.title) like :search escape '\\' or exists(select 1 from entity_tv_shows m"
              + " where m.entity_id=e.id and m.account_id=e.account_id and lower(m.alias) like"
              + " :search escape '\\'))");
    sql.append(" group by 1 order by 1 desc");
    var q =
        em.createNativeQuery(sql.toString(), Object[].class)
            .setParameter("owner", owner)
            .setParameter("type", filter.type().name());
    if (filter.status() != null) q.setParameter("status", filter.status());
    if (filter.query() != null) q.setParameter("search", pattern(filter.query()));
    @SuppressWarnings("unchecked")
    List<Object[]> rows = q.getResultList();
    return rows.stream()
        .map(
            r ->
                new Year(
                    ((Number) r[0]).intValue(),
                    ((Number) r[1]).longValue(),
                    ((Number) r[2]).longValue()))
        .toList();
  }

  public Completion addCompletion(UUID owner, UUID entity, Instant time, Instant now) {
    var event = events.saveAndFlush(new CompletionEntity(owner, entity, time, now));
    em.refresh(event);
    return mapper.completion(event);
  }

  public Optional<Completion> completion(UUID owner, UUID entity, UUID id) {
    return events.findByIdAndAccountIdAndEntityId(id, owner, entity).map(mapper::completion);
  }

  public Completion correctCompletion(UUID owner, UUID id, Instant time) {
    var event = events.findByIdAndAccountId(id, owner).orElseThrow();
    event.correct(time);
    events.flush();
    em.refresh(event);
    return mapper.completion(event);
  }

  public void deleteCompletion(UUID owner, UUID id) {
    events.delete(events.findByIdAndAccountId(id, owner).orElseThrow());
    events.flush();
  }

  public List<Completion> completions(UUID owner, UUID entity, Position position, int limit) {
    var cb = em.getCriteriaBuilder();
    var q = cb.createQuery(CompletionEntity.class);
    var c = q.from(CompletionEntity.class);
    var ps = new ArrayList<Predicate>();
    ps.add(cb.equal(c.get("accountId"), owner));
    ps.add(cb.equal(c.get("entityId"), entity));
    before(cb, c, ps, "completedAt", position);
    q.select(c)
        .where(ps.toArray(Predicate[]::new))
        .orderBy(cb.desc(c.get("completedAt")), cb.desc(c.get("id")));
    return em.createQuery(q).setMaxResults(limit).getResultList().stream()
        .map(mapper::completion)
        .toList();
  }

  public Optional<Link> link(UUID owner, UUID article, UUID entity) {
    return links.findByAccountIdAndArticleIdAndEntityId(owner, article, entity).map(mapper::link);
  }

  public Link addLink(UUID owner, UUID article, UUID entity, Instant now) {
    return mapper.link(links.saveAndFlush(new ArticleEntityLink(owner, article, entity, now)));
  }

  public void unlink(UUID owner, UUID article, UUID entity) {
    links.findByAccountIdAndArticleIdAndEntityId(owner, article, entity).ifPresent(links::delete);
    links.flush();
  }

  public List<LinkedEntity> linkedEntities(UUID owner, UUID article, Position position, int limit) {
    var cb = em.getCriteriaBuilder();
    var q = cb.createTupleQuery();
    var e = q.from(TrackedEntity.class);
    var l = q.from(ArticleEntityLink.class);
    var ps =
        new ArrayList<Predicate>(
            List.of(
                cb.equal(e.get("accountId"), owner),
                cb.equal(l.get("accountId"), owner),
                cb.equal(l.get("articleId"), article),
                cb.equal(l.get("entityId"), e.get("id"))));
    before(cb, e, ps, "createdAt", position);
    q.multiselect(e, l.get("linkedAt"))
        .where(ps.toArray(Predicate[]::new))
        .orderBy(cb.desc(e.get("createdAt")), cb.desc(e.get("id")));
    var rows = em.createQuery(q).setMaxResults(limit).getResultList();
    var all =
        stats(owner, rows.stream().map(r -> r.get(0, TrackedEntity.class).getId()).toList(), null);
    return rows.stream()
        .map(
            r -> {
              var entity = r.get(0, TrackedEntity.class);
              var s = all.getOrDefault(entity.getId(), Stats.EMPTY);
              return mapper.linked(
                  mapper.details(entity, s.last, s.count), r.get(1, Instant.class));
            })
        .toList();
  }

  private Details detail(UUID owner, TrackedEntity e) {
    var s = stats(owner, List.of(e.getId()), null).getOrDefault(e.getId(), Stats.EMPTY);
    return mapper.details(e, s.last, s.count);
  }

  private record Stats(long count, Instant last) {
    static final Stats EMPTY = new Stats(0, null);
  }

  private Map<UUID, Stats> stats(UUID owner, List<UUID> ids, Integer year) {
    if (ids.isEmpty()) return Map.of();
    var cb = em.getCriteriaBuilder();
    var q = cb.createTupleQuery();
    var c = q.from(CompletionEntity.class);
    var ps =
        new ArrayList<Predicate>(
            List.of(cb.equal(c.get("accountId"), owner), c.get("entityId").in(ids)));
    if (year != null) {
      var r = range(year);
      ps.add(cb.greaterThanOrEqualTo(c.get("completedAt"), r[0]));
      ps.add(cb.lessThan(c.get("completedAt"), r[1]));
    }
    q.multiselect(c.get("entityId"), cb.count(c), cb.greatest(c.<Instant>get("completedAt")))
        .where(ps.toArray(Predicate[]::new))
        .groupBy(c.get("entityId"));
    var result = new HashMap<UUID, Stats>();
    for (var row : em.createQuery(q).getResultList())
      result.put(
          row.get(0, UUID.class), new Stats(row.get(1, Long.class), row.get(2, Instant.class)));
    return result;
  }

  private static ArrayList<Predicate> filters(
      CriteriaBuilder cb, Root<TrackedEntity> root, UUID owner, Filter filter) {
    var ps = new ArrayList<Predicate>();
    ps.add(cb.equal(root.get("accountId"), owner));
    ps.add(cb.equal(root.get("type"), filter.type()));
    if (filter.status() != null) ps.add(cb.equal(root.get("status"), filter.status()));
    if (filter.query() != null)
      ps.add(
          cb.or(
              cb.like(cb.lower(root.get("title")), pattern(filter.query()), '\\'),
              cb.like(cb.lower(root.get("tvAlias")), pattern(filter.query()), '\\')));
    return ps;
  }

  private static void before(
      CriteriaBuilder cb, Root<?> root, List<Predicate> ps, String time, Position before) {
    if (before != null)
      ps.add(
          cb.or(
              cb.lessThan(root.get(time), before.time()),
              cb.and(
                  cb.equal(root.get(time), before.time()),
                  cb.lessThan(root.get("id"), before.id()))));
  }

  private static Instant[] range(int year) {
    return new Instant[] {
      LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant(),
      LocalDate.of(year + 1, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
    };
  }

  private static String pattern(String query) {
    return "%"
        + query
            .toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        + "%";
  }

  private static byte[] hash(String title) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(title.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
