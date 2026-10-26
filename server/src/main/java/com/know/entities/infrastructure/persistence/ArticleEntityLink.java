package com.know.entities.infrastructure.persistence;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "article_entities")
@IdClass(ArticleEntityLink.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArticleEntityLink {
  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Id
  @Column(name = "article_id")
  private UUID articleId;

  @Id
  @Column(name = "entity_id")
  private UUID entityId;

  @Column(name = "linked_at", nullable = false)
  private Instant linkedAt;

  public ArticleEntityLink(UUID owner, UUID article, UUID entity, Instant time) {
    accountId = owner;
    articleId = article;
    entityId = entity;
    linkedAt = time;
  }

  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class Key implements Serializable {
    public UUID articleId;
    public UUID entityId;
  }
}
