package com.know.articles.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArticleReader {
  boolean existsOwned(UUID accountId, UUID articleId);

  List<ArticleSummary> findLinkedToEntity(
      UUID accountId, UUID entityId, ArticlePosition before, int limit);

  Optional<ArticleSnapshot> findOwned(UUID accountId, UUID articleId);

  Optional<ArticleSnapshot> findExactUrl(UUID accountId, String url);

  List<ArticleSummary> findNewest(UUID accountId, ArticlePosition before, int limit);

  List<ArticleSummary> findNewest(
      UUID accountId, ArticlePosition before, int limit, String category, String query);
}
