package com.know.articles.application;

import java.util.List;

public record ArticleBatch(List<ArticleSummary> items, ArticlePosition nextPosition) {
  public ArticleBatch {
    items = List.copyOf(items);
  }
}
