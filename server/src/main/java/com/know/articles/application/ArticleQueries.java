package com.know.articles.application;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArticleQueries {
  private final CurrentAccount currentAccount;
  private final ArticleReader reader;
  private final com.know.shared.application.PageLimits limits;
  private final com.know.categories.application.CategoryCatalog categories;

  @Transactional(readOnly = true)
  public ArticleBatch list(ArticlePosition before, int limit) {
    return list(before, limit, null, null);
  }

  @Transactional(readOnly = true)
  public ArticleBatch list(ArticlePosition before, int limit, String category, String query) {
    var account = currentAccount.requireId();
    if (limit < 1 || limit > limits.maxLimit())
      throw new ArticleFailure(
          400, "INVALID_REQUEST", "Limit must be between 1 and " + limits.maxLimit() + ".");
    if (category != null) {
      categories.ensureDefaults(account);
      categories.requireEnabled(account, category);
    }
    var rows =
        category == null && query == null
            ? reader.findNewest(account, before, limit + 1)
            : reader.findNewest(account, before, limit + 1, category, query);
    boolean hasMore = rows.size() > limit;
    var items = rows.subList(0, Math.min(limit, rows.size()));
    var last = hasMore ? items.getLast() : null;
    return new ArticleBatch(
        items, last == null ? null : new ArticlePosition(last.savedAt(), last.id()));
  }

  @Transactional(readOnly = true)
  public ArticleSnapshot detail(UUID id) {
    return reader
        .findOwned(currentAccount.requireId(), id)
        .orElseThrow(() -> new ArticleFailure(404, "ARTICLE_NOT_FOUND", "Article not found."));
  }
}
