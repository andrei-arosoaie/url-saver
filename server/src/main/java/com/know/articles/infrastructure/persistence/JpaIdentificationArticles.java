package com.know.articles.infrastructure.persistence;

import com.know.articles.application.ArticleFailure;
import com.know.articles.application.IdentificationArticles;
import com.know.categories.application.CategoryCatalog;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class JpaIdentificationArticles implements IdentificationArticles {
  private final ArticleJpaRepository articles;
  private final CategoryCatalog categories;
  private final com.know.articles.application.ArticleContent contentPolicy;

  @Transactional(readOnly = true)
  public Input read(UUID account, UUID id) {
    var row =
        articles
            .findByIdAndAccountId(id, account)
            .orElseThrow(() -> new ArticleFailure(404, "ARTICLE_NOT_FOUND", "Article not found."));
    return new Input(
        account,
        id,
        row.getContent(),
        row.getCategory(),
        row.getEntityName(),
        row.getCategorySource(),
        row.getNameSource());
  }

  @Transactional
  public boolean apply(Input input, String category, String name) {
    var row = articles.lockOwned(input.accountId(), input.id()).orElse(null);
    if (row == null || !Objects.equals(input.content(), row.getContent())) return false;
    if (name != null && input.nameMissing() && !row.getNameSource().equals("USER"))
      contentPolicy.validateTitle(name);
    // Check category availability at application time, including concurrent disable/removal.
    if (category != null && input.categoryMissing() && !row.getCategorySource().equals("USER"))
      categories.lockEnabled(input.accountId(), category);
    row.identify(
        input.categoryMissing() && !row.getCategorySource().equals("USER") ? category : null,
        input.nameMissing() && !row.getNameSource().equals("USER") ? name : null);
    articles.flush();
    return true;
  }
}
