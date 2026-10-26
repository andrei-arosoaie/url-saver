package com.know.categories.infrastructure.persistence;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "categories")
@IdClass(CategoryEntity.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoryEntity {
  @Id
  @Column(name = "account_id")
  private UUID accountId;

  @Id private String key;

  @Column(name = "display_name")
  private String displayName;

  private String color;

  @Column(name = "is_default")
  private boolean isDefault;

  private boolean enabled;

  @Column(name = "created_at")
  private Instant createdAt;

  @Version private Long version;

  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class Key implements Serializable {
    public UUID accountId;
    public String key;
  }
}
