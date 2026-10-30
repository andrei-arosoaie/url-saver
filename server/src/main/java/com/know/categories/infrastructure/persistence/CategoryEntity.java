package com.know.categories.infrastructure.persistence;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

  public static CategoryEntity custom(UUID owner, String name, String color) {
    var entity = new CategoryEntity();
    entity.accountId = owner;
    entity.key = UUID.randomUUID().toString();
    entity.displayName = name;
    entity.color = color;
    entity.enabled = true;
    entity.createdAt = Instant.now();
    return entity;
  }

  public void edit(String name, String color, Boolean enabled) {
    if (name != null) this.displayName = name;
    if (color != null) this.color = color;
    if (enabled != null) this.enabled = enabled;
  }

  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class Key implements Serializable {
    public UUID accountId;
    public String key;
  }
}
