package com.know.articles.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArticleReader {
  Optional<ArticleSnapshot> findOwned(UUID accountId, UUID articleId);

  Optional<ArticleSnapshot> findExactUrl(UUID accountId, String url);

  List<ArticleSummary> findNewest(UUID accountId, ArticlePosition before, int limit);
}
