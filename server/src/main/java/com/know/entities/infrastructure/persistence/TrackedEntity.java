package com.know.entities.infrastructure.persistence;

import com.know.entities.application.EntityType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "entities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrackedEntity {
  @Id private UUID id;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private EntityType type;

  @Column(nullable = false, columnDefinition = "text")
  private String title;

  @Column(name = "title_hash", nullable = false)
  private byte[] titleHash;

  @Column(nullable = false)
  private String status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Version private Long version;

  @Column(name = "tvmaze_id", updatable = false)
  private Long tvmazeId;

  @org.hibernate.annotations.Formula("(select m.alias from entity_tv_shows m where m.entity_id=id)")
  private String tvAlias;

  @org.hibernate.annotations.Formula(
      "(select m.new_episodes_available from entity_tv_shows m where m.entity_id=id)")
  private Boolean tvNew;

  @org.hibernate.annotations.Formula(
      "(select 'S' || lpad(e.season_number::text,2,'0') || 'E' ||"
          + " coalesce(lpad(e.number::text,2,'0'),'?') from entity_tv_shows m join tvmaze_episodes"
          + " e on e.tvmaze_id=m.last_watched_episode_id where m.entity_id=id)")
  private String tvPosition;

  public com.know.entities.application.EntityModels.TvSummary tvSummary() {
    return tvmazeId == null
        ? null
        : new com.know.entities.application.EntityModels.TvSummary(
            tvmazeId, tvAlias, tvPosition, Boolean.TRUE.equals(tvNew));
  }

  public void identify(long id) {
    tvmazeId = id;
  }

  public TrackedEntity(
      UUID owner, EntityType type, String title, byte[] hash, String status, Instant createdAt) {
    id = UUID.randomUUID();
    accountId = owner;
    this.type = type;
    this.title = title;
    titleHash = hash.clone();
    this.status = status;
    this.createdAt = createdAt;
  }

  public void revise(String title, byte[] hash, String status) {
    this.title = title;
    titleHash = hash.clone();
    this.status = status;
  }
}
