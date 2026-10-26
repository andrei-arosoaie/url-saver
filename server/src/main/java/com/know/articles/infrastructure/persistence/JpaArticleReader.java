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
  public boolean existsOwned(UUID owner, UUID id) {
    return repository.existsByIdAndAccountId(id, owner);
  }

  public List<ArticleSummary> findLinkedToEntity(
      UUID owner, UUID entity, ArticlePosition before, int limit) {
    return repository
        .findLinkedToEntity(
            owner,
            entity,
            before == null ? null : before.savedAt(),
            before == null ? null : before.id(),
            PageRequest.of(0, limit))
        .stream()
        .map(mapper::toSummary)
        .toList();
  }

  private final ArticleJpaRepository repository;
  private final ArticlePersistenceMapper mapper;
  private final UrlHasher hasher;
  private final jakarta.persistence.EntityManager entityManager;

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
    if (limit < 1 || limit > 1001)
      throw new IllegalArgumentException("Read batch must be between 1 and 101 rows");
    var page = PageRequest.of(0, limit);
    var rows =
        before == null
            ? repository.findNewest(accountId, page)
            : repository.findBefore(accountId, before.savedAt(), before.id(), page);
    return rows.stream().map(mapper::toSummary).toList();
  }

  public List<ArticleSummary> findNewest(
      UUID account, ArticlePosition before, int limit, String category, String query) {
    var cb = entityManager.getCriteriaBuilder();
    var q = cb.createQuery(ArticleSummary.class);
    var a = q.from(ArticleEntity.class);
    var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
    predicates.add(cb.equal(a.get("accountId"), account));
    if (category != null) predicates.add(cb.equal(a.get("category"), category));
    if (query != null && !query.isEmpty()) {
      var pattern =
          "%"
              + query
                  .toLowerCase(java.util.Locale.ROOT)
                  .replace("\\", "\\\\")
                  .replace("%", "\\%")
                  .replace("_", "\\_")
              + "%";
      predicates.add(
          cb.or(
              cb.like(cb.lower(a.get("title")), pattern, '\\'),
              cb.like(cb.lower(a.get("url")), pattern, '\\'),
              cb.like(cb.lower(a.get("entityName")), pattern, '\\')));
    }
    if (before != null)
      predicates.add(
          cb.or(
              cb.lessThan(a.get("savedAt"), before.savedAt()),
              cb.and(
                  cb.equal(a.get("savedAt"), before.savedAt()),
                  cb.lessThan(a.get("id"), before.id()))));
    q.select(
            cb.construct(
                ArticleSummary.class,
                a.get("id"),
                a.get("url"),
                a.get("title"),
                a.get("savedAt"),
                a.get("version"),
                cb.isNotNull(a.get("content")),
                a.get("category"),
                a.get("entityName")))
        .where(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new))
        .orderBy(cb.desc(a.get("savedAt")), cb.desc(a.get("id")));
    return entityManager.createQuery(q).setMaxResults(limit).getResultList();
  }
}
