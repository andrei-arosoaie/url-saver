package com.know.articles.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ArticlePosition(Instant savedAt, UUID id) {
  public ArticlePosition {
    Objects.requireNonNull(savedAt);
    Objects.requireNonNull(id);
  }
}
