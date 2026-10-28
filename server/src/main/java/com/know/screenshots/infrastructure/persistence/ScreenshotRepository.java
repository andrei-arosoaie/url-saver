package com.know.screenshots.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ScreenshotRepository extends JpaRepository<ScreenshotEntity, UUID> {
  Optional<ScreenshotEntity> findByAccountIdAndArticleId(UUID accountId, UUID articleId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from ScreenshotEntity s where s.accountId=:account and s.articleId=:article")
  Optional<ScreenshotEntity> lock(@Param("account") UUID account, @Param("article") UUID article);

  @Modifying
  @Query(
      value =
          "insert into article_screenshots(article_id,account_id,version) values (:article,:account,0) on conflict do nothing",
      nativeQuery = true)
  int ensure(@Param("account") UUID account, @Param("article") UUID article);
}
