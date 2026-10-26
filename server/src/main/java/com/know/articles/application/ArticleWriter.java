package com.know.articles.application;

import java.util.UUID;

public interface ArticleWriter {
  ArticleSnapshot revise(
      UUID accountId, UUID id, String title, String content, String category, String entityName);

  boolean insertIfAbsent(UUID accountId, SaveArticle command);

  ArticleSnapshot lockExactUrl(UUID accountId, String url);

  ArticleSnapshot lockOwned(UUID accountId, UUID id);

  ArticleSnapshot revise(UUID accountId, UUID id, String title, String content);
}
