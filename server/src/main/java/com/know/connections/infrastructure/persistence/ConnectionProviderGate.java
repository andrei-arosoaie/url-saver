package com.know.connections.infrastructure.persistence;

import static com.know.connections.infrastructure.persistence.ConnectionJobs.at;

import com.know.connections.application.ConnectionModels.Job;
import com.know.connections.application.ConnectionModels.Permit;
import com.know.connections.application.ConnectionPolicy;
import java.time.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Reservations are committed before HTTP calls; no transaction spans provider work. */
@Repository
@RequiredArgsConstructor
public class ConnectionProviderGate {
  private final JdbcTemplate jdbc;

  @Transactional
  public Permit acquire(Job job, boolean embedding, ConnectionPolicy p, Instant now) {
    String scope = p.quotaScope();
    jdbc.update(
        """
        insert into embedding_provider_cooldowns(quota_scope,provider,window_started_at,budget_day,updated_at)
        values (?,?,?,?,?) on conflict do nothing
        """,
        scope,
        p.provider(),
        at(now),
        java.sql.Date.valueOf(now.atOffset(ZoneOffset.UTC).toLocalDate()),
        at(now));
    var state =
        jdbc.queryForObject(
            """
        select blocked_until,window_started_at,requests_in_window,budget_day,embeddings_today
        from embedding_provider_cooldowns where quota_scope=? for update
        """,
            (rs, n) ->
                new State(
                    rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(),
                    rs.getTimestamp(2).toInstant(),
                    rs.getInt(3),
                    rs.getDate(4).toLocalDate(),
                    rs.getInt(5)),
            scope);
    if (state.blocked() != null && state.blocked().isAfter(now))
      return waiting(scope, state.blocked(), "PROVIDER_QUOTA");
    var day = now.atOffset(ZoneOffset.UTC).toLocalDate();
    int used = day.equals(state.day()) ? state.embeddings() : 0;
    if (embedding && used >= p.embeddingsPerDay())
      return waiting(
          scope, day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC), "DAILY_BUDGET");
    boolean fresh = !now.isBefore(state.window().plusSeconds(60));
    Instant window = fresh ? now : state.window();
    int requests = fresh ? 0 : state.requests();
    if (requests >= p.requestsPerMinute())
      return waiting(scope, window.plusSeconds(60), "REQUEST_BUDGET");
    jdbc.update(
        "delete from embedding_provider_permits where quota_scope=? and lease_until<=?",
        scope,
        at(now));
    var available =
        jdbc.queryForList(
            "select lease_until from embedding_provider_permits where quota_scope=? order by lease_until",
            java.sql.Timestamp.class,
            scope);
    if (available.size() >= p.globalConcurrency())
      return waiting(
          scope,
          available.getFirst().toInstant().isBefore(now.plus(p.pollInterval()))
              ? available.getFirst().toInstant()
              : now.plus(p.pollInterval()),
          "PROVIDER_BUSY");
    if (!Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
        select exists(select 1 from article_connection_jobs where id=? and lease_token=?
        and status='RUNNING' and lease_until>?)
        """,
            Boolean.class,
            job.id(),
            job.leaseToken(),
            at(now)))) throw ConnectionJobs.stale();
    UUID token = UUID.randomUUID();
    jdbc.update(
        "insert into embedding_provider_permits(lease_token,quota_scope,job_id,lease_until) values (?,?,?,?)",
        token,
        scope,
        job.id(),
        at(now.plus(p.leaseDuration())));
    jdbc.update(
        """
        update embedding_provider_cooldowns set window_started_at=?,requests_in_window=?,budget_day=?,
        embeddings_today=?,updated_at=? where quota_scope=?
        """,
        at(window),
        requests + 1,
        java.sql.Date.valueOf(day),
        used + (embedding ? 1 : 0),
        at(now),
        scope);
    return new Permit(token, scope, null, null);
  }

  @Transactional
  public void release(Permit permit) {
    if (permit != null && permit.acquired())
      jdbc.update("delete from embedding_provider_permits where lease_token=?", permit.token());
  }

  @Transactional
  public void rateLimited(String scope, Instant until, Instant now) {
    jdbc.update(
        """
        update embedding_provider_cooldowns set blocked_until=greatest(blocked_until,?),
        reason='RATE_LIMITED',last_http_status=429,updated_at=? where quota_scope=?
        """,
        at(until),
        at(now),
        scope);
  }

  public Instant blockedUntil(String scope, Instant now) {
    return jdbc
        .query(
            "select blocked_until from embedding_provider_cooldowns where quota_scope=? and blocked_until>?",
            (rs, n) -> rs.getTimestamp(1).toInstant(),
            scope,
            at(now))
        .stream()
        .findFirst()
        .orElse(null);
  }

  private static Permit waiting(String scope, Instant until, String reason) {
    return new Permit(null, scope, until, reason);
  }

  private record State(
      Instant blocked, Instant window, int requests, LocalDate day, int embeddings) {}
}
