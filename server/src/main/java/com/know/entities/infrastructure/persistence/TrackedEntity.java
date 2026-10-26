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
