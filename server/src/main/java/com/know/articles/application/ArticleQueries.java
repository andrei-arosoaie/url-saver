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

  @Transactional(readOnly = true)
  public ArticleBatch list(ArticlePosition before, int limit) {
    var account = currentAccount.requireId();
    if (limit < 1 || limit > 100)
      throw new ArticleFailure(400, "INVALID_REQUEST", "Limit must be between 1 and 100.");
    var rows = reader.findNewest(account, before, limit + 1);
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
