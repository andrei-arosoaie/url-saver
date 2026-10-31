package com.know.connections.infrastructure.persistence;

import static com.know.connections.application.ConnectionModels.*;
import static com.know.connections.infrastructure.persistence.ConnectionJobs.at;

import com.know.connections.application.*;
import com.know.shared.application.ScopedCursor;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ConnectionPairs {
  private final JdbcTemplate jdbc;
  private final ConnectionJobs jobs;
  private final java.time.Clock clock;

  public List<UUID> candidates(Job job) {
    return jdbc.queryForList(
        "select id from articles where account_id=? and id<>? and content is not null order by id",
        UUID.class,
        job.accountId(),
        job.articleId());
  }

  /** Both endpoint revisions stay locked throughout local evaluation and publication. */
  @Transactional
  public void evaluate(Job job, UUID other, ConnectionPolicy policy, Instant now) {
    var pair = pair(job.articleId(), other);
    lock(job.accountId(), pair);
    var endpoints =
        jdbc.queryForList(
            "select id from articles where account_id=? and id in (?,?) order by id for update",
            UUID.class,
            job.accountId(),
            pair.low(),
            pair.high());
    if (endpoints.size() != 2 || !jobs.current(job, clock.instant())) return;
    if (excluded(job.accountId(), pair)) return;
    var low = jobs.article(job.accountId(), pair.low());
    var high = jobs.article(job.accountId(), pair.high());
    if (low.content() == null || high.content() == null) return;
    var entities =
        jdbc.queryForList(
            """
        select a.entity_id from article_entities a join article_entities b
        on b.account_id=a.account_id and b.entity_id=a.entity_id
        where a.account_id=? and a.article_id=? and b.article_id=? order by a.entity_id
        """,
            UUID.class,
            job.accountId(),
            pair.low(),
            pair.high());
    byte[] lowHash = readyHash(job.accountId(), pair.low(), job.profileId(), low.revision());
    byte[] highHash = readyHash(job.accountId(), pair.high(), job.profileId(), high.revision());
    boolean evaluated = lowHash != null && highHash != null;
    Double score =
        evaluated ? score(job.accountId(), pair, job.profileId(), lowHash, highHash) : null;
    evaluated = evaluated && score != null;
    if (!jobs.current(job, clock.instant())) return;
    Double accepted = evaluated && score >= policy.threshold() ? score : null;
    var existing =
        jdbc
            .query(
                "select id,semantic_score from article_connections where account_id=? and article_low_id=? and article_high_id=?",
                (rs, n) -> new Existing(rs.getObject(1, UUID.class), rs.getObject(2, Double.class)),
                job.accountId(),
                pair.low(),
                pair.high())
            .stream()
            .findFirst()
            .orElse(null);
    if (!evaluated && existing != null) accepted = existing.score();
    if (entities.isEmpty() && accepted == null) {
      if (existing != null) erase(job.accountId(), existing.id());
      return;
    }
    UUID id = existing == null ? UUID.randomUUID() : existing.id();
    if (existing == null)
      jdbc.update(
          """
        insert into article_connections(id,account_id,article_low_id,article_high_id,low_revision,high_revision,evaluated_at,created_at,updated_at)
        values (?,?,?,?,?,?,?,?,?)
        """,
          id,
          job.accountId(),
          pair.low(),
          pair.high(),
          low.revision(),
          high.revision(),
          at(now),
          at(now),
          at(now));
    if (evaluated)
      jdbc.update(
          """
        update article_connections set semantic_score=?,semantic_profile_id=?,low_content_hash=?,high_content_hash=?,
        low_revision=?,high_revision=?,evaluated_at=?,updated_at=?,version=version+1 where account_id=? and id=?
        """,
          accepted,
          accepted == null ? null : job.profileId(),
          accepted == null ? null : lowHash,
          accepted == null ? null : highHash,
          low.revision(),
          high.revision(),
          at(now),
          at(now),
          job.accountId(),
          id);
    jdbc.update(
        "delete from connection_shared_entities where account_id=? and connection_id=?",
        job.accountId(),
        id);
    for (UUID entity : entities)
      jdbc.update(
          "insert into connection_shared_entities(account_id,connection_id,entity_id,created_at) values (?,?,?,?)",
          job.accountId(),
          id,
          entity,
          at(now));
  }

  private byte[] readyHash(UUID owner, UUID article, UUID profile, long revision) {
    return jdbc
        .query(
            """
        select content_hash from article_connection_jobs where account_id=? and article_id=? and kind='EMBEDDING'
        and status='SUCCEEDED' and profile_id=? and processed_profile_id=? and processed_revision=?
        """,
            (rs, n) -> rs.getBytes(1),
            owner,
            article,
            profile,
            profile,
            revision)
        .stream()
        .findFirst()
        .orElse(null);
  }

  private Double score(UUID owner, Pair p, UUID profile, byte[] low, byte[] high) {
    return jdbc.queryForObject(
        """
        with similarities as (
          select a.chunk_index ai,a.token_count aw,b.chunk_index bi,b.token_count bw,
          greatest(-1.0,least(1.0,1-(a.embedding <=> b.embedding))) similarity
          from article_embeddings a join article_embeddings b on b.account_id=a.account_id and b.profile_id=a.profile_id
          where a.account_id=? and a.profile_id=? and a.article_id=? and b.article_id=?
          and a.content_hash=? and b.content_hash=?
        ), forward as (select ai,max(aw) weight,max(similarity) best from similarities group by ai),
        backward as (select bi,max(bw) weight,max(similarity) best from similarities group by bi)
        select ((select sum(weight*best)/nullif(sum(weight),0) from forward)+
        (select sum(weight*best)/nullif(sum(weight),0) from backward))/2
        """,
        Double.class,
        owner,
        profile,
        p.low(),
        p.high(),
        low,
        high);
  }

  public List<Related> related(UUID owner, UUID source, ScopedCursor.Position before, int limit) {
    var rows =
        jdbc.query(
            """
        select a.id,c.id,a.title,a.identified_entity_name,a.category,a.url,a.saved_at,c.semantic_score,
        (c.semantic_score is not null and (c.low_revision<>lo.connection_revision or c.high_revision<>hi.connection_revision
        or c.semantic_profile_id is distinct from lj.profile_id or c.semantic_profile_id is distinct from hj.profile_id)) updating
        from article_connections c join articles a on a.account_id=c.account_id
        and a.id=case when c.article_low_id=? then c.article_high_id else c.article_low_id end
        join articles lo on lo.id=c.article_low_id join articles hi on hi.id=c.article_high_id
        left join article_connection_jobs lj on lj.account_id=c.account_id and lj.article_id=lo.id and lj.kind='EMBEDDING'
        left join article_connection_jobs hj on hj.account_id=c.account_id and hj.article_id=hi.id and hj.kind='EMBEDDING'
        where c.account_id=? and (c.article_low_id=? or c.article_high_id=?)
        and (cast(? as timestamptz) is null or (a.saved_at,a.id)<(?,?))
        order by a.saved_at desc,a.id desc limit ?
        """,
            (rs, n) ->
                new Related(
                    rs.getObject(1, UUID.class),
                    rs.getObject(2, UUID.class),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getString(5),
                    rs.getString(6),
                    rs.getTimestamp(7).toInstant(),
                    List.of(),
                    rs.getObject(8, Double.class),
                    rs.getBoolean(9)),
            source,
            owner,
            source,
            source,
            before == null ? null : at(before.time()),
            before == null ? null : at(before.time()),
            before == null ? null : before.id(),
            limit);
    return rows.stream()
        .map(
            r ->
                new Related(
                    r.id(),
                    r.connectionId(),
                    r.title(),
                    r.entityName(),
                    r.category(),
                    r.url(),
                    r.savedAt(),
                    jdbc.query(
                        "select e.id,e.title from connection_shared_entities c join entities e on e.account_id=c.account_id and e.id=c.entity_id where c.account_id=? and c.connection_id=? order by e.title,e.id",
                        (rs, n) -> new SharedEntity(rs.getObject(1, UUID.class), rs.getString(2)),
                        owner,
                        r.connectionId()),
                    r.semanticScore(),
                    r.semanticUpdating()))
        .toList();
  }

  @Transactional
  public void remove(UUID owner, UUID source, UUID other, Instant now) {
    jobs.article(owner, source);
    jobs.article(owner, other);
    if (source.equals(other))
      throw new com.know.shared.application.RequestFailure(
          400, "INVALID_REQUEST", "An article cannot connect to itself.");
    var p = pair(source, other);
    lock(owner, p);
    if (jdbc.queryForList(
                "select id from articles where account_id=? and id in (?,?) order by id for update",
                UUID.class,
                owner,
                p.low(),
                p.high())
            .size()
        != 2)
      throw new com.know.shared.application.RequestFailure(
          404, "ARTICLE_NOT_FOUND", "Article not found.");
    jdbc.update(
        "insert into article_connection_exclusions(account_id,article_low_id,article_high_id,removed_at) values (?,?,?,?) on conflict do nothing",
        owner,
        p.low(),
        p.high(),
        at(now));
    jdbc.queryForList(
            "select id from article_connections where account_id=? and article_low_id=? and article_high_id=?",
            UUID.class,
            owner,
            p.low(),
            p.high())
        .forEach(id -> erase(owner, id));
  }

  @Transactional
  public void delete(UUID owner, UUID source) {
    // Match heartbeat's job -> permit order before removing either row.
    jdbc.queryForList(
        "select id from article_connection_jobs where account_id=? and article_id=? order by id for update",
        UUID.class,
        owner,
        source);
    jdbc.update(
        "delete from embedding_provider_permits where job_id in (select id from article_connection_jobs where account_id=? and article_id=?)",
        owner,
        source);
    jdbc.update(
        "delete from article_connection_jobs where account_id=? and article_id=?", owner, source);
    jdbc.update(
        "delete from article_embeddings where account_id=? and article_id=?", owner, source);
    jdbc.update(
        "delete from article_connection_exclusions where account_id=? and (article_low_id=? or article_high_id=?)",
        owner,
        source,
        source);
    jdbc.queryForList(
            "select id from article_connections where account_id=? and (article_low_id=? or article_high_id=?)",
            UUID.class,
            owner,
            source,
            source)
        .forEach(id -> erase(owner, id));
  }

  private void erase(UUID owner, UUID id) {
    jdbc.update(
        "delete from connection_shared_entities where account_id=? and connection_id=?", owner, id);
    jdbc.update("delete from article_connections where account_id=? and id=?", owner, id);
  }

  private boolean excluded(UUID owner, Pair p) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 from article_connection_exclusions where account_id=? and article_low_id=? and article_high_id=?)",
            Boolean.class,
            owner,
            p.low(),
            p.high()));
  }

  private void lock(UUID owner, Pair p) {
    jdbc.query(
        "select pg_advisory_xact_lock(hashtextextended(?,0))",
        rs -> {},
        owner + "|" + p.low() + "|" + p.high());
  }

  private static Pair pair(UUID a, UUID b) {
    return a.toString().compareTo(b.toString()) < 0 ? new Pair(a, b) : new Pair(b, a);
  }

  private record Pair(UUID low, UUID high) {}

  private record Existing(UUID id, Double score) {}
}
