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
}
