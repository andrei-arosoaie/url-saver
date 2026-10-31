package com.know.identification.infrastructure.persistence;

import com.know.identification.application.*;
import com.know.shared.application.RequestFailure;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
@RequiredArgsConstructor
public class JdbcIdentificationStore implements IdentificationStore {
  private final JdbcTemplate jdbc;
  private final JsonMapper json;

  @Transactional
  public void enqueue(UUID account, UUID article, Instant now, boolean explicit) {
    // Lock article first: the same lock order is used when completing/deleting a job.
    if (jdbc.queryForList(
            "select id from articles where account_id=? and id=? for update", account, article)
        .isEmpty()) throw new RequestFailure(404, "ARTICLE_NOT_FOUND", "Article not found.");
    jdbc.update(
        """
        insert into identification_jobs(article_id,account_id,status,requested_at)
        values (?,?,'PENDING',?) on conflict(article_id) do nothing
        """,
        article,
        account,
        Timestamp.from(now));
    if (explicit)
      jdbc.update(
          """
        update identification_jobs set status='PENDING',reason=null,provider=null,model=null,
          subjects='[]',requested_at=?,completed_at=null,claim_id=null,lease_until=null
        where article_id=? and account_id=? and status not in ('PENDING','RUNNING')
        """,
          Timestamp.from(now),
          article,
          account);
  }

  @Transactional(readOnly = true)
  public Identification.Status status(UUID account, UUID article) {
    var rows =
        jdbc.query(
            """
        select * from identification_jobs where account_id=? and article_id=?
        """,
            (rs, row) ->
                new Identification.Status(
                    rs.getString("status"),
                    rs.getString("reason"),
                    rs.getString("provider"),
                    rs.getString("model"),
                    json.readValue(
                        rs.getString("subjects"),
                        new TypeReference<List<Identification.Subject>>() {}),
                    null,
                    null,
                    rs.getTimestamp("requested_at").toInstant(),
                    rs.getTimestamp("completed_at") == null
                        ? null
                        : rs.getTimestamp("completed_at").toInstant()),
            account,
            article);
    return rows.isEmpty()
        ? new Identification.Status(
            "NOT_REQUESTED", null, null, null, List.of(), null, null, null, null)
        : rows.getFirst();
  }

  @Transactional
  public Identification.Job claim(Instant now, Duration lease) {
    var claim = UUID.randomUUID();
    var rows =
        jdbc.query(
            """
        update identification_jobs set status='RUNNING',claim_id=?,lease_until=?
        where article_id=(select article_id from identification_jobs
          where status='PENDING' or (status='RUNNING' and lease_until <= ?)
          order by requested_at,article_id for update skip locked limit 1)
        returning account_id,article_id
        """,
            (rs, row) ->
                new Identification.Job(
                    rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), claim),
            claim,
            Timestamp.from(now.plus(lease)),
            Timestamp.from(now));
    return rows.isEmpty() ? null : rows.getFirst();
  }

  @Transactional
  public void finish(
      Identification.Job job,
      String status,
      String reason,
      String provider,
      String model,
      List<Identification.Subject> subjects,
      Instant now) {
    // Serialize with article updates/deletion before locking the job.
    jdbc.queryForList(
        "select id from articles where account_id=? and id=? for update",
        job.accountId(),
        job.articleId());
    var changed =
        jdbc.update(
            """
        update identification_jobs set status=?,reason=?,provider=?,model=?,subjects=cast(? as jsonb),
          completed_at=?,lease_until=null,claim_id=null
        where account_id=? and article_id=? and claim_id=? and status='RUNNING' and lease_until>?
        """,
            status,
            reason,
            provider,
            model,
            json.writeValueAsString(subjects),
            Timestamp.from(now),
            job.accountId(),
            job.articleId(),
            job.claimId(),
            Timestamp.from(now));
    if (changed == 0)
      throw new RequestFailure(
          409, "IDENTIFICATION_CLAIM_EXPIRED", "Identification claim expired.");
  }

  @Transactional
  public Identification.ProviderPermit acquire(String provider, Instant now, Duration lease) {
    jdbc.update(
        "insert into identification_provider_health(provider) values (?) on conflict do nothing",
        provider);
    var claim = UUID.randomUUID();
    var changed =
        jdbc.update(
            """
        update identification_provider_health set claim_id=?,lease_until=?,
          consecutive_failures=case when cooldown_until is not null then 0 else consecutive_failures end,
          cooldown_until=null
        where provider=? and (cooldown_until is null or cooldown_until<=?)
          and (lease_until is null or lease_until<=?)
        """,
            claim,
            Timestamp.from(now.plus(lease)),
            provider,
            Timestamp.from(now),
            Timestamp.from(now));
    return changed == 1 ? new Identification.ProviderPermit(provider, claim) : null;
  }

  @Transactional
  public void success(Identification.ProviderPermit permit) {
    jdbc.update(
        """
        update identification_provider_health set consecutive_failures=0,cooldown_until=null,
          last_reason=null,claim_id=null,lease_until=null where provider=? and claim_id=?
        """,
        permit.provider(),
        permit.claimId());
  }

  @Transactional
  public void failure(
      Identification.ProviderPermit permit,
      boolean quota,
      IdentificationPolicy policy,
      Instant now) {
    jdbc.update(
        """
        update identification_provider_health set consecutive_failures=consecutive_failures+1,
          cooldown_until=case when ? or consecutive_failures+1>=? then cast(? as timestamptz) else null end,
          last_reason=?,claim_id=null,lease_until=null where provider=? and claim_id=?
        """,
        quota,
        policy.consecutiveFailureThreshold(),
        Timestamp.from(now.plus(policy.providerCooldown())),
        quota ? "QUOTA_EXHAUSTED" : "PROVIDER_FAILURE",
        permit.provider(),
        permit.claimId());
  }
}
