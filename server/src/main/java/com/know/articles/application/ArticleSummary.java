package com.know.articles.application;

import java.time.Instant;
import java.util.UUID;

public record ArticleSummary(
    UUID id,
    String url,
    String title,
    Instant savedAt,
    long version,
    boolean hasContent,
    String category,
    String entityName) {
  public ArticleSummary(
      UUID id, String url, String title, Instant savedAt, long version, boolean hasContent) {
    this(id, url, title, savedAt, version, hasContent, "OTHER", null);
  }
}
