package com.know.articles.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ArticleJpaRepository extends JpaRepository<ArticleEntity, UUID> {
  boolean existsByIdAndAccountId(UUID id, UUID accountId);

  @Query(
      value =
          """
      select a.id as id, a.url as url, a.title as title, a.saved_at as "savedAt",
        a.version as version, (a.content is not null) as "hasContent",
        a.category as category, a.identified_entity_name as "entityName"
      from articles a join article_entities l on l.article_id=a.id and l.account_id=a.account_id
      where a.account_id=:accountId and l.entity_id=:entityId
        and (cast(:savedAt as timestamptz) is null or a.saved_at < :savedAt
          or (a.saved_at=:savedAt and a.id < :id))
      order by a.saved_at desc,a.id desc
      """,
      nativeQuery = true)
  List<ArticleSummaryRow> findLinkedToEntity(
      UUID accountId, UUID entityId, Instant savedAt, UUID id, Pageable page);

  @Modifying
  @Query(
      value =
          """
      insert into articles(id, account_id, url, url_hash, title, content, saved_at, version)
      values (:id, :accountId, :url, :hash, :title, :content, :savedAt, 0)
      on conflict (account_id, url_hash) do nothing
      """,
      nativeQuery = true)
  int insertIfAbsent(
      UUID id,
      UUID accountId,
      String url,
      byte[] hash,
      String title,
      String content,
      Instant savedAt);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from ArticleEntity a where a.accountId = :accountId and a.urlHash = :hash")
  Optional<ArticleEntity> lockByUrl(UUID accountId, byte[] hash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from ArticleEntity a where a.accountId = :accountId and a.id = :id")
  Optional<ArticleEntity> lockOwned(UUID accountId, UUID id);

  Optional<ArticleEntity> findByIdAndAccountId(UUID id, UUID accountId);

  Optional<ArticleEntity> findByAccountIdAndUrlHash(UUID accountId, byte[] urlHash);

  @Query(
      """
      select a.id as id, a.url as url, a.title as title, a.savedAt as savedAt,
        a.version as version, (a.content is not null) as hasContent,
        a.category as category, a.entityName as entityName
      from ArticleEntity a where a.accountId = :accountId
      order by a.savedAt desc, a.id desc
      """)
  List<ArticleSummaryRow> findNewest(UUID accountId, Pageable page);

  @Query(
      """
      select a.id as id, a.url as url, a.title as title, a.savedAt as savedAt,
        a.version as version, (a.content is not null) as hasContent,
        a.category as category, a.entityName as entityName
      from ArticleEntity a where a.accountId = :accountId
        and (a.savedAt < :savedAt or (a.savedAt = :savedAt and a.id < :id))
      order by a.savedAt desc, a.id desc
      """)
  List<ArticleSummaryRow> findBefore(UUID accountId, Instant savedAt, UUID id, Pageable page);
}
