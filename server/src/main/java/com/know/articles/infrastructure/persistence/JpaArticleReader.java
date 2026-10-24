package com.know.articles.infrastructure.persistence;

import com.know.articles.application.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaArticleReader implements ArticleReader {
  private final ArticleJpaRepository repository;
  private final ArticlePersistenceMapper mapper;
  private final UrlHasher hasher;

  @Override
  public Optional<ArticleSnapshot> findOwned(UUID accountId, UUID articleId) {
    return repository.findByIdAndAccountId(articleId, accountId).map(mapper::toSnapshot);
  }

  @Override
  public Optional<ArticleSnapshot> findExactUrl(UUID accountId, String url) {
    return repository
        .findByAccountIdAndUrlHash(accountId, hasher.sha256(url))
        .map(
            entity -> {
              if (!entity.getUrl().equals(url)) throw new UrlHashCollisionException();
              return mapper.toSnapshot(entity);
            });
  }

  @Override
  public List<ArticleSummary> findNewest(UUID accountId, ArticlePosition before, int limit) {
    if (limit < 1 || limit > 101)
      throw new IllegalArgumentException("Read batch must be between 1 and 101 rows");
    var page = PageRequest.of(0, limit);
    var rows =
        before == null
            ? repository.findNewest(accountId, page)
            : repository.findBefore(accountId, before.savedAt(), before.id(), page);
    return rows.stream().map(mapper::toSummary).toList();
  }
}
