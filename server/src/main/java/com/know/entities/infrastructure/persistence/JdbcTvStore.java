package com.know.entities.infrastructure.persistence;

import static com.know.entities.application.TvModels.*;

import com.know.entities.application.TvStore;
import java.sql.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Repository
@RequiredArgsConstructor
public class JdbcTvStore implements TvStore {
  private final JdbcTemplate jdbc;
  private final JsonMapper json;

  public void lockCatalog(long show) {
    jdbc.queryForList(
        "select tvmaze_id from tvmaze_shows where tvmaze_id=? for share", Long.class, show);
  }

  private static Timestamp at(Instant time) {
    return time == null ? null : Timestamp.from(time);
  }

  private static Instant instant(ResultSet rs, String name) throws SQLException {
    var value = rs.getTimestamp(name);
    return value == null ? null : value.toInstant();
  }

  public Optional<Mapping> mapping(UUID owner, UUID entity) {
    return jdbc
        .query(
            "select * from entity_tv_shows where account_id=? and entity_id=?",
            (rs, n) ->
                new Mapping(
                    entity,
                    rs.getLong("show_id"),
                    rs.getString("alias"),
                    rs.getObject("last_watched_episode_id", Long.class),
                    instant(rs, "last_marked_seen_at"),
                    rs.getBoolean("new_episodes_available"),
                    rs.getLong("version")),
            owner,
            entity)
        .stream()
        .findFirst();
  }

  public Optional<UUID> mappedEntity(UUID owner, long show) {
    return jdbc
        .query(
            "select entity_id from entity_tv_shows where account_id=? and show_id=?",
            (rs, n) -> rs.getObject(1, UUID.class),
            owner,
            show)
        .stream()
        .findFirst();
  }

  private void seed(Show show) {
    jdbc.update(
        "insert into tvmaze_shows(tvmaze_id,name) values (?,?) on conflict do nothing",
        show.tvmazeId(),
        show.name());
  }

  public void link(UUID owner, UUID entity, Show show, Instant now) {
    seed(show);
    jdbc.update(
        "update entities set tvmaze_id=? where account_id=? and id=?",
        show.tvmazeId(),
        owner,
        entity);
    jdbc.update(
        "insert into entity_tv_shows(account_id,entity_id,show_id,created_at,updated_at) values"
            + " (?,?,?,?,?)",
        owner,
        entity,
        show.tvmazeId(),
        at(now),
        at(now));
  }

  public void replace(UUID owner, UUID entity, Show show, Instant now) {
    seed(show);
    jdbc.update(
        "delete from entity_episode_watches where account_id=? and entity_id=?", owner, entity);
    jdbc.update(
        "update entity_tv_shows set"
            + " show_id=?,last_watched_episode_id=null,last_marked_seen_at=null,new_episodes_available=false,version=version+1,updated_at=?"
            + " where account_id=? and entity_id=?",
        show.tvmazeId(),
        at(now),
        owner,
        entity);
    jdbc.update(
        "update entities set tvmaze_id=? where account_id=? and id=?",
        show.tvmazeId(),
        owner,
        entity);
  }

  public void alias(UUID owner, UUID entity, String value, Instant now) {
    jdbc.update(
        "update entity_tv_shows set alias=?,version=version+1,updated_at=? where account_id=? and"
            + " entity_id=?",
        value,
        at(now),
        owner,
        entity);
  }

  public void position(UUID owner, UUID entity, long episode, Instant now) {
    jdbc.update(
        "update entity_tv_shows set last_watched_episode_id=?,updated_at=? where account_id=? and"
            + " entity_id=?",
        episode,
        at(now),
        owner,
        entity);
  }

  public boolean watch(UUID owner, UUID entity, long episode, boolean watched, Instant now) {
    return (watched
            ? jdbc.update(
                "insert into entity_episode_watches(account_id,entity_id,episode_id,watched_at)"
                    + " values (?,?,?,?) on conflict do nothing",
                owner,
                entity,
                episode,
                at(now))
            : jdbc.update(
                "delete from entity_episode_watches where account_id=? and entity_id=? and"
                    + " episode_id=?",
                owner,
                entity,
                episode))
        > 0;
  }

  public void seen(UUID owner, UUID entity, Instant now) {
    jdbc.update(
        "update entity_tv_shows set"
            + " last_marked_seen_at=?,new_episodes_available=false,version=version+1,updated_at=?"
            + " where account_id=? and entity_id=?",
        at(now),
        at(now),
        owner,
        entity);
  }

  public void clearNew(UUID owner, UUID entity, Instant now) {
    jdbc.update(
        "update entity_tv_shows set new_episodes_available=false,version=version+1,updated_at=?"
            + " where account_id=? and entity_id=?",
        at(now),
        owner,
        entity);
  }

  public void bump(UUID owner, UUID entity, Instant now) {
    jdbc.update(
        "update entity_tv_shows set version=version+1,updated_at=? where account_id=? and"
            + " entity_id=?",
        at(now),
        owner,
        entity);
  }

  public Show show(long id) {
    return jdbc
        .query(
            "select payload from tvmaze_shows where tvmaze_id=? and fetched_at is not null",
            (rs, n) -> json.readValue(rs.getString(1), Show.class),
            id)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public Metadata metadata(long id) {
    return jdbc.queryForObject(
        "select s.fetched_at,j.available_at,j.last_error_code from tvmaze_shows s left join"
            + " tvmaze_refresh_jobs j on j.show_id=s.tvmaze_id where s.tvmaze_id=?",
        (rs, n) ->
            new Metadata(
                rs.getString("last_error_code") != null
                    ? "UNAVAILABLE"
                    : rs.getTimestamp("fetched_at") == null ? "PENDING" : "READY",
                instant(rs, "fetched_at"),
                instant(rs, "available_at")),
        id);
  }

  public List<Season> seasons(UUID owner, Mapping m, Instant now) {
    return jdbc.query(
        """
        select s.*,count(e.tvmaze_id) as regular_count,
          count(e.tvmaze_id) filter(where e.air_at<=?) as aired_count,
          count(w.episode_id) filter(where e.air_at<=?) as watched_count
        from tvmaze_seasons s left join tvmaze_episodes e on e.season_id=s.tvmaze_id
          and e.show_id=s.show_id and e.episode_type='regular' and e.provider_present
        left join entity_episode_watches w on w.episode_id=e.tvmaze_id and w.account_id=? and w.entity_id=?
        where s.show_id=? group by s.tvmaze_id order by s.number,s.tvmaze_id
        """,
        (rs, n) ->
            new Season(
                rs.getLong("tvmaze_id"),
                rs.getInt("number"),
                rs.getString("name"),
                rs.getLong("regular_count"),
                rs.getLong("aired_count"),
                rs.getLong("watched_count")),
        at(now),
        at(now),
        owner,
        m.entityId(),
        m.tvmazeId());
  }

  public List<Episode> episodes(UUID owner, Mapping m, Instant now) {
    return jdbc.query(
        """
        select e.*,w.episode_id is not null as watched from tvmaze_episodes e
        left join entity_episode_watches w on w.episode_id=e.tvmaze_id and w.account_id=? and w.entity_id=?
        where e.show_id=? and e.episode_type='regular' and e.provider_present
        order by e.season_number,e.number nulls last,e.tvmaze_id
        """,
        (rs, n) -> {
          var e = json.readValue(rs.getString("payload"), Episode.class);
          boolean future =
              e.airAt() != null
                  ? e.airAt().isAfter(now)
                  : e.airDate() != null
                      && e.airDate().isAfter(now.atOffset(ZoneOffset.UTC).toLocalDate());
          return new Episode(
              e.id(),
              e.showId(),
              e.seasonId(),
              e.season(),
              e.number(),
              e.title(),
              e.airAt(),
              e.airDate(),
              e.runtime(),
              e.imageUrl(),
              e.summary(),
              rs.getBoolean("watched"),
              !future);
        },
        owner,
        m.entityId(),
        m.tvmazeId());
  }

  public void enqueue(long id, Instant now) {
    jdbc.update(
        "insert into tvmaze_refresh_jobs(show_id,available_at) values (?,?) on conflict do nothing",
        id,
        at(now));
  }

  @Transactional
  public void enqueueDue(Instant now, Instant staleBefore) {
    jdbc.update(
        """
        insert into tvmaze_refresh_jobs(show_id,available_at)
        select distinct s.tvmaze_id,cast(? as timestamptz) from tvmaze_shows s join entity_tv_shows m on m.show_id=s.tvmaze_id
        where s.fetched_at is null or s.fetched_at<? on conflict do nothing
        """,
        at(now),
        at(staleBefore));
  }

  @Transactional
  public Optional<Lease> claim(Instant now, Instant until) {
    var token = UUID.randomUUID();
    return jdbc
        .query(
            """
            update tvmaze_refresh_jobs set lease_token=?,lease_until=?,attempt_count=attempt_count+1
            where show_id=(select show_id from tvmaze_refresh_jobs where available_at<=?
              and (lease_until is null or lease_until<=?) order by available_at limit 1 for update skip locked)
            returning show_id
            """,
            (rs, n) -> new Lease(rs.getLong(1), token),
            token,
            at(until),
            at(now),
            at(now))
        .stream()
        .findFirst();
  }

  @Transactional
  public void publish(Lease lease, Catalog catalog, Instant now) {
    var active =
        jdbc.queryForList(
            "select show_id from tvmaze_refresh_jobs where show_id=? and lease_token=? and"
                + " lease_until>? for update",
            Long.class,
            lease.showId(),
            lease.token(),
            at(now));
    if (active.isEmpty()) return;
    long id = lease.showId();
    jdbc.queryForList(
        "select tvmaze_id from tvmaze_shows where tvmaze_id=? for update", Long.class, id);
    for (var s : catalog.seasons())
      jdbc.update(
          """
          insert into tvmaze_seasons(tvmaze_id,show_id,number,name,payload,fetched_at) values (?,?,?,?,?::jsonb,?)
          on conflict(tvmaze_id) do update set number=excluded.number,name=excluded.name,payload=excluded.payload,fetched_at=excluded.fetched_at
          where tvmaze_seasons.show_id=excluded.show_id
          """,
          s.id(),
          id,
          s.number(),
          s.name(),
          json.writeValueAsString(s),
          at(now));
    jdbc.update("update tvmaze_episodes set provider_present=false where show_id=?", id);
    for (var e : catalog.episodes())
      jdbc.update(
          """
          insert into tvmaze_episodes(tvmaze_id,show_id,season_id,season_number,number,episode_type,name,air_at,air_date,payload,fetched_at)
          values (?,?,?,?,?,'regular',?,?,?,?::jsonb,?) on conflict(tvmaze_id) do update set
            season_id=excluded.season_id,season_number=excluded.season_number,number=excluded.number,name=excluded.name,
            air_at=excluded.air_at,air_date=excluded.air_date,payload=excluded.payload,fetched_at=excluded.fetched_at,provider_present=true
          where tvmaze_episodes.show_id=excluded.show_id
          """,
          e.id(),
          id,
          e.seasonId(),
          e.season(),
          e.number(),
          e.title(),
          at(e.airAt()),
          e.airDate() == null ? null : java.sql.Date.valueOf(e.airDate()),
          json.writeValueAsString(e),
          at(now));
    jdbc.update(
        "update tvmaze_shows set name=?,payload=?::jsonb,fetched_at=?,version=version+1 where"
            + " tvmaze_id=?",
        catalog.show().name(),
        json.writeValueAsString(catalog.show()),
        at(now),
        id);
    jdbc.update(
        "delete from tvmaze_refresh_jobs where show_id=? and lease_token=?", id, lease.token());
  }

  @Transactional
  public void failed(Lease lease, String reason, Instant retryAt) {
    jdbc.update(
        "update tvmaze_refresh_jobs set"
            + " lease_token=null,lease_until=null,available_at=?,last_error_code=? where show_id=?"
            + " and lease_token=?",
        at(retryAt),
        reason,
        lease.showId(),
        lease.token());
  }

  @Transactional
  public void evaluateAirings(Instant now) {
    // Same account advisory lock as user writes; short transactions never overlap network calls.
    var owners =
        jdbc.queryForList(
            """
            select distinct m.account_id from entity_tv_shows m where not m.new_episodes_available
            and m.last_marked_seen_at is not null and exists(
              select 1 from tvmaze_episodes e where e.show_id=m.show_id and e.provider_present
              and e.episode_type='regular' and e.air_at>m.last_marked_seen_at and e.air_at<=?
              and not exists(select 1 from entity_episode_watches w where w.account_id=m.account_id
                and w.entity_id=m.entity_id and w.episode_id=e.tvmaze_id))
            order by m.account_id limit 100
            """,
            UUID.class,
            at(now));
    for (var owner : owners) {
      jdbc.queryForList(
          "select pg_advisory_xact_lock(hashtextextended(?,0))", "know-entities:" + owner);
      var changed =
          jdbc.query(
              """
              update entity_tv_shows m set new_episodes_available=true,version=m.version+1,updated_at=?
              where m.account_id=? and not m.new_episodes_available and m.last_marked_seen_at is not null
              and exists(select 1 from tvmaze_shows s where s.tvmaze_id=m.show_id and s.fetched_at is not null)
              and exists(select 1 from tvmaze_episodes e where e.show_id=m.show_id and e.provider_present
                and e.episode_type='regular' and e.air_at>m.last_marked_seen_at and e.air_at<=?
                and not exists(select 1 from entity_episode_watches w where w.account_id=m.account_id and w.entity_id=m.entity_id and w.episode_id=e.tvmaze_id))
              returning m.entity_id
              """,
              (rs, n) -> rs.getObject(1, UUID.class),
              at(now),
              owner,
              at(now));
      for (var id : changed)
        jdbc.update(
            "update entities set status=case when status='SEEN' then 'TO_BE_SEEN' else status"
                + " end,version=version+1 where account_id=? and id=?",
            owner,
            id);
    }
  }

  public Optional<CachedSearch> cached(String key) {
    return jdbc
        .query(
            "select * from tvmaze_search_cache where query_key=? and result_payload is not null",
            (rs, n) ->
                new CachedSearch(
                    Arrays.asList(
                        json.readValue(rs.getString("result_payload"), Candidate[].class)),
                    instant(rs, "fetched_at"),
                    instant(rs, "expires_at")),
            key)
        .stream()
        .findFirst();
  }

  @Transactional
  public boolean claimSearch(String key, String query, UUID token, Instant now, Instant until) {
    return jdbc.update(
            """
            insert into tvmaze_search_cache(query_key,normalized_query,refresh_lease_token,refresh_lease_until) values (?,?,?,?)
            on conflict(query_key) do update set refresh_lease_token=excluded.refresh_lease_token,refresh_lease_until=excluded.refresh_lease_until
            where (tvmaze_search_cache.refresh_lease_until is null or tvmaze_search_cache.refresh_lease_until<=?)
              and (tvmaze_search_cache.expires_at is null or tvmaze_search_cache.expires_at<=?)
            """,
            key,
            query,
            token,
            at(until),
            at(now),
            at(now))
        > 0;
  }

  @Transactional
  public void cache(String key, UUID token, List<Candidate> items, Instant now, Instant expires) {
    jdbc.update(
        "update tvmaze_search_cache set"
            + " result_payload=?::jsonb,fetched_at=?,expires_at=?,refresh_lease_token=null,refresh_lease_until=null"
            + " where query_key=? and refresh_lease_token=? and refresh_lease_until>?",
        json.writeValueAsString(items),
        at(now),
        at(expires),
        key,
        token,
        at(now));
  }

  @Transactional
  public void failSearch(String key, UUID token, Instant retryAt) {
    jdbc.update(
        "update tvmaze_search_cache set refresh_lease_token=null,refresh_lease_until=? where"
            + " query_key=? and refresh_lease_token=?",
        at(retryAt),
        key,
        token);
  }

  @Transactional
  public void cleanup(Instant before, int limit) {
    jdbc.update(
        "delete from tvmaze_search_cache where query_key in (select query_key from"
            + " tvmaze_search_cache where (refresh_lease_until is null or"
            + " refresh_lease_until<CURRENT_TIMESTAMP) order by fetched_at desc nulls last offset ?"
            + " limit 100)",
        limit);
    jdbc.update(
        "delete from tvmaze_search_cache where query_key in (select query_key from"
            + " tvmaze_search_cache where expires_at<? and (refresh_lease_until is null or"
            + " refresh_lease_until<CURRENT_TIMESTAMP) limit 100)",
        at(before));
  }

  @Transactional
  public boolean claimProvider(UUID token, Instant now, Instant until) {
    return jdbc.update(
            "update tvmaze_provider_state set lease_token=?,lease_until=? where"
                + " quota_scope='public' and (lease_until is null or lease_until<=?) and"
                + " (blocked_until is null or blocked_until<=?)",
            token,
            at(until),
            at(now),
            at(now))
        > 0;
  }

  @Transactional
  public void releaseProvider(UUID token, Instant until) {
    jdbc.update(
        "update tvmaze_provider_state set lease_token=null,lease_until=null,blocked_until=? where"
            + " quota_scope='public' and lease_token=?",
        at(until),
        token);
  }
}
