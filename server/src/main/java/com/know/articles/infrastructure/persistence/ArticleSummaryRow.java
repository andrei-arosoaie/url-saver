package com.know.articles.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

public interface ArticleSummaryRow {
  UUID getId();

  String getUrl();

  String getTitle();

  Instant getSavedAt();

  Long getVersion();

  boolean getHasContent();
}
