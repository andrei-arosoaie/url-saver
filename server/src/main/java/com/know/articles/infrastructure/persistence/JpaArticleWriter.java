package com.know.articles.infrastructure.persistence;

import com.know.articles.application.*;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaArticleWriter implements ArticleWriter {
  private final ArticleJpaRepository repository;
  private final ArticlePersistenceMapper mapper;
  private final UrlHasher hasher;

  public void delete(UUID accountId, UUID id) {
    var entity = owned(accountId, id);
    repository.unlinkAll(accountId, id);
    repository.delete(entity);
    repository.flush();
  }

  public boolean insertIfAbsent(UUID accountId, SaveArticle command) {
    return repository.insertIfAbsent(
            UUID.randomUUID(),
            accountId,
            command.url(),
            hasher.sha256(command.url()),
            command.title(),
            command.content(),
            Instant.now())
        == 1;
  }

  public ArticleSnapshot lockExactUrl(UUID accountId, String url) {
    var entity = repository.lockByUrl(accountId, hasher.sha256(url)).orElseThrow();
    if (!entity.getUrl().equals(url)) throw new UrlHashCollisionException();
    return mapper.toSnapshot(entity);
  }

  public ArticleSnapshot lockOwned(UUID accountId, UUID id) {
    return mapper.toSnapshot(owned(accountId, id));
  }

  public ArticleSnapshot revise(UUID accountId, UUID id, String title, String content) {
    var entity = owned(accountId, id);
    entity.revise(title, content);
    repository.flush();
    return mapper.toSnapshot(entity);
  }

  public ArticleSnapshot revise(
      UUID accountId, UUID id, String title, String content, String category, String entityName) {
    var entity = owned(accountId, id);
    entity.revise(title, content, category, entityName);
    repository.flush();
    return mapper.toSnapshot(entity);
  }

  private ArticleEntity owned(UUID accountId, UUID id) {
    return repository
        .lockOwned(accountId, id)
        .orElseThrow(() -> new ArticleFailure(404, "ARTICLE_NOT_FOUND", "Article not found."));
  }
}
