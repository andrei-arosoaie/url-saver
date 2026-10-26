package com.know.articles.application;

import java.time.Instant;
import java.util.UUID;

public record ArticleSnapshot(
    UUID id,
    UUID accountId,
    String url,
    String title,
    String content,
    Instant savedAt,
    long version,
    String category,
    String entityName) {
  public ArticleSnapshot(
      UUID id,
      UUID accountId,
      String url,
      String title,
      String content,
      Instant savedAt,
      long version) {
    this(id, accountId, url, title, content, savedAt, version, "OTHER", null);
  }
}
