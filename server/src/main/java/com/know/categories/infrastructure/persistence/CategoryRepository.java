package com.know.categories.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

public interface CategoryRepository extends JpaRepository<CategoryEntity, CategoryEntity.Key> {
  List<CategoryEntity> findByAccountIdOrderByCreatedAtAscKeyAsc(UUID account);

  Optional<CategoryEntity> findByAccountIdAndKey(UUID account, String key);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from CategoryEntity c where c.accountId=:account and c.key=:key")
  Optional<CategoryEntity> lock(UUID account, String key);

  @Query(
      value =
          "select category as key, count(*) as count from articles where account_id=:account group by category",
      nativeQuery = true)
  List<ArticleCount> counts(UUID account);

  @Query(
      value = "select count(*) from articles where account_id=:account and category=:key",
      nativeQuery = true)
  long countArticles(UUID account, String key);

  interface ArticleCount {
    String getKey();

    long getCount();
  }

  @Modifying
  @Query(
      value =
          """
    insert into categories(account_id,key,display_name,color,is_default,enabled,created_at,version)
    select :account,c.key,c.name,c.color,true,true,current_timestamp,0 from (values
    ('ARCHITECTURE','Architecture','#305ea7'),('DEVELOPMENT','Development','#8654b0'),('AI','AI','#0891b2'),('OTHER','Other','#be923b'),
    ('BOOKS','Books','#168e91'),('MOVIES','Movies','#d66f57'),('TV_SHOWS','TV shows','#c65e96'),('GAMES','Games','#4a9a73')) as c(key,name,color)
    on conflict(account_id,key) do nothing
    """,
      nativeQuery = true)
  int seed(UUID account);
}
