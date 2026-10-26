package com.know.articles.application;

import java.util.UUID;
import lombok.Getter;

@Getter
public class ArticleFailure extends com.know.shared.application.RequestFailure {
  private final UUID existingArticleId;

  public ArticleFailure(int status, String reason, String message) {
    this(status, reason, message, null);
  }

  public ArticleFailure(int status, String reason, String message, UUID existingArticleId) {
    super(status, reason, message);
    this.existingArticleId = existingArticleId;
  }
}
