package com.know.connections.infrastructure.persistence;

import static com.know.connections.application.ConnectionModels.*;

import com.know.connections.application.*;
import com.know.shared.application.ScopedCursor;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcConnectionStore implements ConnectionStore {
  public int resumeConfiguredWork(UUID profile, Instant now) {
    return jobs.resumeConfiguredWork(profile, now);
  }

  private final ConnectionJobs jobs;
  private final ConnectionPairs pairs;
  private final ConnectionProviderGate gate;
  private final JdbcTemplate jdbc;

  public Profile profile(ConnectionPolicy p, Instant now) {
    return jobs.profile(p, now);
  }

  public Article article(UUID owner, UUID id) {
    return jobs.article(owner, id);
  }

  public void changed(UUID owner, UUID id, Profile p, Instant now, boolean increment) {
    jobs.changed(owner, id, p, now, increment);
  }

  public void retry(UUID owner, UUID id, Profile p, Instant now) {
    jobs.retry(owner, id, p, now);
  }

  public Job claim(Instant now, Duration lease) {
    return jobs.claim(now, lease);
  }

  public boolean renew(Job j, UUID permit, Instant now, Duration lease) {
    return jobs.renew(j, permit, now, lease);
  }

  public boolean current(Job j, Instant now) {
    return jobs.current(j, now);
  }

  public void defer(Job j, Instant until, String code, String message, Instant now) {
    jobs.defer(j, until, code, message, now);
  }

  public void failed(Job j, EmbeddingFailure f, ConnectionPolicy p, Instant now) {
    jobs.failed(j, f, p, now);
  }

  public void finish(Job j, byte[] hash, Instant now) {
    jobs.finish(j, hash, now);
  }

  public Permit acquire(Job j, boolean embedding, ConnectionPolicy p, Instant now) {
    return gate.acquire(j, embedding, p, now);
  }

  public void release(Permit permit) {
    gate.release(permit);
  }

  public void rateLimited(String scope, Instant until, Instant now) {
    gate.rateLimited(scope, until, now);
  }

  public List<Chunk> plan(Job j, byte[] hash) {
    return jobs.plan(j, hash);
  }

  public void plan(Job j, byte[] hash, List<Chunk> chunks, Instant now) {
    jobs.plan(j, hash, chunks, now);
  }

  public boolean cached(Job j, byte[] hash, int index, Chunk chunk) {
    return jobs.cached(j, hash, index, chunk);
  }

  public void vector(
      Job j, Article a, byte[] hash, int index, Chunk chunk, float[] vector, Instant now) {
    jobs.vector(j, a, hash, index, chunk, vector, now);
  }

  public void connect(Job j, ConnectionPolicy p, Instant now) {
    for (UUID other : pairs.candidates(j)) {
      if (!jobs.current(j, now)) return;
      pairs.evaluate(j, other, p, now);
    }
  }

  public List<Related> related(UUID owner, UUID source, ScopedCursor.Position before, int limit) {
    return pairs.related(owner, source, before, limit);
  }

  public void remove(UUID owner, UUID source, UUID other, Instant now) {
    pairs.remove(owner, source, other, now);
  }

  public void delete(UUID owner, UUID source) {
    pairs.delete(owner, source);
  }

  public int backfill(Profile p, int size, Instant now) {
    return jobs.backfill(p, size, now);
  }

  public Progress progress(
      UUID owner, UUID source, Profile profile, ConnectionPolicy p, Instant now) {
    var article = jobs.article(owner, source);
    long poll = p.uiPollInterval().toMillis();
    if (!p.enabled()) return new Progress("DISABLED", null, null, poll);
    if (article.content() == null) return new Progress("SKIPPED", "NO_ARTICLE_TEXT", null, poll);
    var states =
        jdbc.query(
            "select status,last_error_code,available_at from article_connection_jobs where account_id=? and article_id=? and profile_id=?",
            (rs, n) -> new State(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant()),
            owner,
            source,
            profile.id());
    if (states.stream().anyMatch(s -> s.status().equals("FAILED")))
      return new Progress("FAILED", "PROCESSING_FAILED", null, poll);
    if (states.stream().anyMatch(s -> s.status().equals("RUNNING")))
      return new Progress("PROCESSING", null, null, poll);
    var pending =
        states.stream()
            .filter(s -> s.status().equals("PENDING"))
            .min(Comparator.comparing(State::available))
            .orElse(null);
    if (pending != null) {
      if (!p.liveRequestsEnabled()
          && states.stream().anyMatch(s -> "LIVE_REQUESTS_DISABLED".equals(s.reason())))
        return new Progress("PAUSED", "LIVE_REQUESTS_DISABLED", null, poll);
      var blocked = gate.blockedUntil(p.quotaScope(), now);
      if (blocked != null) return new Progress("PAUSED", "PROVIDER_QUOTA", blocked, poll);
      return new Progress(
          pending.reason() == null ? "QUEUED" : "RETRY_PENDING",
          pending.reason(),
          pending.available(),
          poll);
    }
    return new Progress(states.size() == 2 ? "COMPLETE" : "QUEUED", null, null, poll);
  }

  private record State(String status, String reason, Instant available) {}
}
