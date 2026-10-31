package com.know.articles.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "articles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArticleEntity {
  @Id private UUID id;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(nullable = false, columnDefinition = "text")
  private String url;

  @Column(name = "url_hash", nullable = false)
  private byte[] urlHash;

  @Column(nullable = false, columnDefinition = "text")
  private String title;

  @Column(columnDefinition = "text")
  private String content;

  @Column(nullable = false)
  private String category = "OTHER";

  @Column(name = "identified_entity_name")
  private String entityName;

  @Column(name = "category_source", nullable = false)
  private String categorySource = "DEFAULT";

  @Column(name = "name_source", nullable = false)
  private String nameSource = "DEFAULT";

  public void markManual(boolean categoryPresent, boolean namePresent) {
    if (categoryPresent) categorySource = "USER";
    if (namePresent) nameSource = "USER";
  }

  public void identify(String category, String name) {
    if (!categorySource.equals("USER") && category != null) {
      this.category = category;
      categorySource = "AI";
    }
    if (!nameSource.equals("USER") && name != null) {
      entityName = name;
      nameSource = "AI";
    }
  }

  @Column(name = "saved_at", nullable = false)
  private Instant savedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  public ArticleEntity(
      UUID id,
      UUID accountId,
      String url,
      byte[] urlHash,
      String title,
      String content,
      Instant savedAt) {
    this.id = id;
    this.accountId = accountId;
    this.url = url;
    this.urlHash = urlHash.clone();
    this.title = title;
    this.content = content;
    this.savedAt = savedAt;
  }

  public void replaceContent(String content) {
    this.content = content;
  }

  public void revise(String title, String content) {
    this.title = title;
    this.content = content;
  }

  public void revise(String title, String content, String category, String entityName) {
    revise(title, content);
    this.category = category;
    this.entityName = entityName;
  }
}
