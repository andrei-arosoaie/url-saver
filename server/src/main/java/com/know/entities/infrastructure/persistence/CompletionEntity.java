package com.know.entities.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "entity_completions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompletionEntity {
  @Id private UUID id;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(name = "entity_id", nullable = false)
  private UUID entityId;

  @Column(name = "completed_at", nullable = false)
  private Instant completedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Version private Long version;

  public CompletionEntity(UUID owner, UUID entity, Instant time, Instant created) {
    id = UUID.randomUUID();
    accountId = owner;
    entityId = entity;
    completedAt = time;
    createdAt = created;
  }

  public void correct(Instant time) {
    completedAt = time;
  }
}
