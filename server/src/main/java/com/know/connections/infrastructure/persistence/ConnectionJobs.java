package com.know.connections.infrastructure.persistence;

import static com.know.connections.application.ConnectionModels.*;

import com.know.connections.application.*;
import com.know.shared.application.RequestFailure;
import java.sql.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
@RequiredArgsConstructor
public class ConnectionJobs {
  private final JdbcTemplate jdbc;
  private final JsonMapper json;
  private final ApplicationEventPublisher events;

  static Timestamp at(Instant now) {
    return Timestamp.from(now);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Profile profile(ConnectionPolicy p, Instant now) {
    String hash =
        EmbeddingMath.hex(
            "text-v1|"
                + p.provider()
                + "|"
                + p.model()
                + "|SEMANTIC_SIMILARITY|"
                + p.dimensions()
                + "|"
                + p.chunkCharacters()
                + "|"
                + p.overlapCharacters()
                + "|"
                + p.maxTokens());
    byte[] matching = EmbeddingMath.hash("symmetric-token-weighted-best-v1|" + p.threshold());
    var existing =
        jdbc.query(
            "select id,provider,model,dimensions from embedding_profiles where config_hash=decode(?,'hex') and matching_config_hash=?",
            (rs, n) ->
                new Profile(
                    rs.getObject(1, UUID.class),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getInt(4),
                    hash),
            hash,
            matching);
    if (!existing.isEmpty()) return existing.getFirst();
    var id = UUID.randomUUID();
    jdbc.update(
        """
        insert into embedding_profiles(id,provider,model,dimensions,chunking_config,config_hash,matching_config_hash,created_at)
        values (?,?,?,?,cast(? as jsonb),decode(?,'hex'),?,?) on conflict(config_hash) do update
        set matching_config_hash=excluded.matching_config_hash,
          backfill_after=case when embedding_profiles.matching_config_hash<>excluded.matching_config_hash then null else embedding_profiles.backfill_after end,
          backfill_complete=case when embedding_profiles.matching_config_hash<>excluded.matching_config_hash then false else embedding_profiles.backfill_complete end
        """,
        id,
        p.provider(),
        p.model(),
        p.dimensions(),
        json.writeValueAsString(
            Map.of(
                "textVersion",
                1,
                "chunkCharacters",
                p.chunkCharacters(),
                "overlapCharacters",
                p.overlapCharacters(),
                "maxTokens",
                p.maxTokens())),
        hash,
        matching,
        at(now));
    return jdbc.queryForObject(
        "select id,provider,model,dimensions from embedding_profiles where config_hash=decode(?,'hex')",
        (rs, n) ->
            new Profile(
                rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getInt(4), hash),
        hash);
  }

  @Transactional(readOnly = true)
  public Article article(UUID owner, UUID id) {
    return jdbc
        .query(
            "select account_id,id,content,version,connection_revision from articles where account_id=? and id=?",
            (rs, n) ->
                new Article(
                    rs.getObject(1, UUID.class),
                    rs.getObject(2, UUID.class),
                    rs.getString(3),
                    rs.getLong(4),
                    rs.getLong(5)),
            owner,
            id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new RequestFailure(404, "ARTICLE_NOT_FOUND", "Article not found."));
  }

  @Transactional
  public void changed(UUID owner, UUID id, Profile profile, Instant now, boolean increment) {
    if (increment)
      jdbc.update(
          "update articles set connection_revision=connection_revision+1 where account_id=? and id=?",
          owner,
          id);
    if (profile == null) return;
    var a = article(owner, id);
    if (a.content() == null) return;
    put(a, Kind.CONNECTIONS, profile.id(), now, false);
    put(a, Kind.EMBEDDING, profile.id(), now, false);
  }

  private void put(Article a, Kind kind, UUID profile, Instant now, boolean force) {
    int changed =
        jdbc.update(
            """
        insert into article_connection_jobs(id,account_id,article_id,kind,profile_id,requested_revision,status,available_at,created_at,updated_at)
        values (?,?,?,?,?,?,'PENDING',?,?,?) on conflict(account_id,article_id,kind) do update set
          requested_revision=greatest(article_connection_jobs.requested_revision,excluded.requested_revision),
          content_hash=case when article_connection_jobs.profile_id<>excluded.profile_id then null else article_connection_jobs.content_hash end,
          chunk_plan=case when article_connection_jobs.profile_id<>excluded.profile_id then '[]'::jsonb else article_connection_jobs.chunk_plan end,
          profile_id=excluded.profile_id,
          status=case when article_connection_jobs.status='RUNNING' then 'RUNNING' else 'PENDING' end,
          rerun_requested=article_connection_jobs.rerun_requested or (article_connection_jobs.status='RUNNING' and ?),
          attempt_count=case when article_connection_jobs.status='RUNNING' then article_connection_jobs.attempt_count else 0 end,
          available_at=excluded.available_at,updated_at=excluded.updated_at,last_error_code=null,last_error_message=null
        where article_connection_jobs.requested_revision<excluded.requested_revision
          or article_connection_jobs.profile_id<>excluded.profile_id or ?
        """,
            UUID.randomUUID(),
            a.accountId(),
            a.id(),
            kind.name(),
            profile,
            a.revision(),
            at(now),
            at(now),
            at(now),
            force,
            force);
    if (changed > 0) events.publishEvent(new ConnectionWorkAvailable());
  }

  @Transactional
  public void retry(UUID owner, UUID id, Profile profile, Instant now) {
    var a = article(owner, id);
    if (a.content() == null) return;
    for (var kind : Kind.values()) {
      boolean failed =
          Boolean.TRUE.equals(
              jdbc.queryForObject(
                  "select exists(select 1 from article_connection_jobs where account_id=? and article_id=? and kind=? and status='FAILED')",
                  Boolean.class,
                  owner,
                  id,
                  kind.name()));
      put(a, kind, profile.id(), now, failed);
    }
  }

  @Transactional
  public Job claim(Instant now, Duration lease) {
    var token = UUID.randomUUID();
    var rows =
        jdbc.query(
            """
        update article_connection_jobs set status='RUNNING',lease_token=?,lease_until=?,claimed_revision=requested_revision,updated_at=?,rerun_requested=false
        where id=(select id from article_connection_jobs where (status='PENDING' and available_at<=?)
          or (status='RUNNING' and lease_until<=?)
          order by case when kind='CONNECTIONS' then 0 else 1 end,available_at,id for update skip locked limit 1)
        returning *
        """,
            (rs, n) -> job(rs),
            token,
            at(now.plus(lease)),
            at(now),
            at(now),
            at(now));
    return rows.isEmpty() ? null : rows.getFirst();
  }

  private Job job(ResultSet rs) throws SQLException {
    return new Job(
        rs.getObject("id", UUID.class),
        rs.getObject("account_id", UUID.class),
        rs.getObject("article_id", UUID.class),
        Kind.valueOf(rs.getString("kind")),
        rs.getObject("profile_id", UUID.class),
        rs.getLong("claimed_revision"),
        rs.getObject("lease_token", UUID.class),
        rs.getInt("attempt_count"),
        rs.getBytes("content_hash"),
        json.readValue(rs.getString("chunk_plan"), new TypeReference<List<Chunk>>() {}));
  }

  @Transactional(readOnly = true)
  public boolean current(Job job, Instant now) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
        select exists(select 1 from article_connection_jobs j join articles a on a.id=j.article_id and a.account_id=j.account_id
        where j.id=? and j.lease_token=? and j.status='RUNNING' and j.lease_until>? and j.profile_id=?
          and j.requested_revision=? and a.connection_revision=?)
        """,
            Boolean.class,
            job.id(),
            job.leaseToken(),
            at(now),
            job.profileId(),
            job.revision(),
            job.revision()));
  }

  @Transactional
  public boolean renew(Job job, UUID permit, Instant now, Duration lease) {
    int changed =
        jdbc.update(
            "update article_connection_jobs set lease_until=? where id=? and lease_token=? and status='RUNNING' and lease_until>?",
            at(now.plus(lease)),
            job.id(),
            job.leaseToken(),
            at(now));
    if (changed != 1) return false;
    return permit == null
        || jdbc.update(
                "update embedding_provider_permits set lease_until=? where lease_token=? and job_id=? and lease_until>?",
                at(now.plus(lease)),
                permit,
                job.id(),
                at(now))
            == 1;
  }

  @Transactional
  public void defer(Job job, Instant until, String code, String message, Instant now) {
    jdbc.update(
        """
        update article_connection_jobs set status='PENDING',available_at=?,lease_token=null,lease_until=null,
          last_error_code=?,last_error_message=?,updated_at=? where id=? and lease_token=? and status='RUNNING'
        """,
        at(until),
        code,
        message,
        at(now),
        job.id(),
        job.leaseToken());
  }

  @Transactional
  public void failed(Job job, EmbeddingFailure failure, ConnectionPolicy policy, Instant now) {
    if (!current(job, now)) {
      defer(job, now, null, null, now);
      return;
    }
    boolean retry = failure.transientFailure() && job.attempts() < policy.retryDelays().size();
    Instant next = retry ? now.plus(policy.retryDelays().get(job.attempts())) : now;
    if (failure.retryAfter() != null && failure.retryAfter().isAfter(next))
      next = failure.retryAfter();
    jdbc.update(
        """
        update article_connection_jobs set status=?,available_at=?,attempt_count=attempt_count+1,
          last_error_code=?,last_error_message=?,lease_token=null,lease_until=null,updated_at=? where id=? and lease_token=?
        """,
        retry ? "PENDING" : "FAILED",
        at(next),
        failure.transientFailure() ? "PROVIDER_UNAVAILABLE" : "INVALID_PROVIDER_REQUEST",
        retry ? "Provider request failed; retry scheduled." : "Connections could not be generated.",
        at(now),
        job.id(),
        job.leaseToken());
  }

  @Transactional
  public void finish(Job job, byte[] hash, Instant now) {
    jdbc.queryForList(
        "select id from articles where account_id=? and id=? for update",
        UUID.class,
        job.accountId(),
        job.articleId());
    var a = article(job.accountId(), job.articleId());
    if (!current(job, now)) {
      defer(job, now, null, null, now);
      return;
    }
    int changed =
        jdbc.update(
            """
        update article_connection_jobs set status=case when rerun_requested then 'PENDING' else 'SUCCEEDED' end,
          available_at=?,rerun_requested=false,processed_revision=?,processed_profile_id=?,content_hash=?,
          last_error_code=null,last_error_message=null,lease_token=null,lease_until=null,updated_at=?
        where id=? and lease_token=? and requested_revision=? and profile_id=?
        """,
            at(now),
            job.revision(),
            job.profileId(),
            hash,
            at(now),
            job.id(),
            job.leaseToken(),
            job.revision(),
            job.profileId());
    if (changed == 1 && job.kind() == Kind.EMBEDDING)
      put(a, Kind.CONNECTIONS, job.profileId(), now, true);
  }

  @Transactional(readOnly = true)
  public List<Chunk> plan(Job job, byte[] hash) {
    var rows =
        jdbc.query(
            "select chunk_plan from article_connection_jobs where id=? and lease_token=? and profile_id=? and content_hash=?",
            (rs, n) -> json.readValue(rs.getString(1), new TypeReference<List<Chunk>>() {}),
            job.id(),
            job.leaseToken(),
            job.profileId(),
            hash);
    return rows.isEmpty() ? List.of() : rows.getFirst();
  }

  @Transactional
  public void plan(Job job, byte[] hash, List<Chunk> chunks, Instant now) {
    if (jdbc.update(
            "update article_connection_jobs set content_hash=?,chunk_plan=cast(? as jsonb),updated_at=? where id=? and lease_token=? and profile_id=? and lease_until>?",
            hash,
            json.writeValueAsString(chunks),
            at(now),
            job.id(),
            job.leaseToken(),
            job.profileId(),
            at(now))
        != 1) throw stale();
  }

  @Transactional(readOnly = true)
  public boolean cached(Job job, byte[] hash, int index, Chunk chunk) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
        select exists(select 1 from article_embeddings where account_id=? and article_id=? and profile_id=?
        and content_hash=? and chunk_index=? and text_start=? and text_end=?)
        """,
            Boolean.class,
            job.accountId(),
            job.articleId(),
            job.profileId(),
            hash,
            index,
            chunk.start(),
            chunk.end()));
  }

  @Transactional
  public void vector(
      Job job, Article a, byte[] hash, int index, Chunk chunk, float[] vector, Instant now) {
    if (!Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 from article_connection_jobs where id=? and lease_token=? and status='RUNNING' and lease_until>?)",
            Boolean.class,
            job.id(),
            job.leaseToken(),
            at(now)))) throw stale();
    jdbc.update(
        """
        insert into article_embeddings(id,account_id,article_id,profile_id,content_hash,source_article_version,chunk_index,text_start,text_end,token_count,embedding,created_at)
        values (?,?,?,?,?,?,?,?,?,?,cast(? as vector),?) on conflict(account_id,article_id,profile_id,content_hash,chunk_index) do nothing
        """,
        UUID.randomUUID(),
        a.accountId(),
        a.id(),
        job.profileId(),
        hash,
        a.version(),
        index,
        chunk.start(),
        chunk.end(),
        chunk.tokens(),
        Arrays.toString(vector),
        at(now));
  }

  @Transactional
  public int backfill(Profile profile, int size, Instant now) {
    var cursors =
        jdbc.query(
            "select backfill_after,backfill_complete from embedding_profiles where id=? for update skip locked",
            (rs, n) -> new Object[] {rs.getObject(1, UUID.class), rs.getBoolean(2)},
            profile.id());
    if (cursors.isEmpty()) return 0;
    var cursor = cursors.getFirst();
    boolean complete = (boolean) cursor[1];
    var rows =
        jdbc.query(
            """
        select account_id,id,connection_revision from articles a where content is not null
        and ((not ?) and (cast(? as uuid) is null or id>cast(? as uuid))
          or (? and exists(select 1 from (values ('EMBEDDING'),('CONNECTIONS')) k(kind)
            where not exists(select 1 from article_connection_jobs j where j.account_id=a.account_id and j.article_id=a.id
              and j.kind=k.kind and j.profile_id=? and j.requested_revision>=a.connection_revision))))
        order by id limit ?
        """,
            (rs, n) ->
                new Article(
                    rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), "", 0, rs.getLong(3)),
            complete,
            cursor[0],
            cursor[0],
            complete,
            profile.id(),
            size);
    for (var a : rows) {
      put(a, Kind.EMBEDDING, profile.id(), now, false);
      put(a, Kind.CONNECTIONS, profile.id(), now, !complete);
    }
    if (!complete)
      jdbc.update(
          "update embedding_profiles set backfill_after=?,backfill_complete=? where id=?",
          rows.isEmpty() ? cursor[0] : rows.getLast().id(),
          rows.size() < size,
          profile.id());
    return rows.size();
  }

  static RequestFailure stale() {
    return new RequestFailure(409, "CONNECTION_WORK_CHANGED", "Connection work changed.");
  }
}
