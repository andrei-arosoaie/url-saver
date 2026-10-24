package com.know.articles.application;

import java.util.UUID;
import lombok.Getter;

@Getter
public class ArticleFailure extends RuntimeException {
  private final int status;
  private final String reason;
  private final UUID existingArticleId;

  public ArticleFailure(int status, String reason, String message) {
    this(status, reason, message, null);
  }

  public ArticleFailure(int status, String reason, String message, UUID existingArticleId) {
    super(message);
    this.status = status;
    this.reason = reason;
    this.existingArticleId = existingArticleId;
  }
}
