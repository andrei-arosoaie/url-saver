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
  public ArticleSnapshot detail(UUID id) {
    return reader
        .findOwned(currentAccount.requireId(), id)
        .orElseThrow(() -> new ArticleFailure(404, "ARTICLE_NOT_FOUND", "Article not found."));
  }
}
