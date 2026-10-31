package com.know.articles.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;

import com.know.articles.application.*;
import com.know.support.BackendIntegrationSupport;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor(onConstructor_ = @Autowired)
class ArticlePersistenceIT extends BackendIntegrationSupport {
  private final JdbcTemplate jdbc;
  private final ArticleReader reader;
  private final EntityManagerFactory entityManagers;
  private final UrlHasher hasher;
  private UUID owner;
  private UUID other;
  private final Instant savedAt = Instant.parse("2026-10-24T10:00:00Z");

  @BeforeEach
  void fixtures() {
    jdbc.execute(
        "TRUNCATE identification_jobs, identification_provider_health, screenshot_cleanup, article_screenshots, entity_completions, article_entities, entities, articles, categories, accounts");
    owner = UUID.randomUUID();
    other = UUID.randomUUID();
    account(owner);
    account(other);
  }

  @Test
  void flywayCreatesValidatedSchemaAndNativeHashCheck() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class))
        .isEqualTo(6);
    assertThat(
            jdbc.queryForObject(
                "select encode(sha256(convert_to('abc','UTF8')),'hex')", String.class))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }

  @Test
  void readsOnlyOwnedArticleAndPreservesHtml() {
    var id =
        insert(owner, "https://example.com/é?x=1", "<p>All article text — stays.</p>", savedAt);
    assertThat(reader.findOwned(owner, id))
        .hasValueSatisfying(
            a -> {
              assertThat(a.content()).isEqualTo("<p>All article text — stays.</p>");
              assertThat(a.version()).isZero();
            });
    assertThat(reader.findOwned(other, id)).isEmpty();
    assertThat(reader.findOwned(owner, UUID.randomUUID())).isEmpty();
  }

  @Test
  void exactUrlLookupNeverNormalizesAndIsAccountScoped() {
    var id = insert(owner, "https://example.com/path", null, savedAt);
    assertThat(reader.findExactUrl(owner, "https://example.com/path"))
        .hasValueSatisfying(a -> assertThat(a.id()).isEqualTo(id));
    assertThat(reader.findExactUrl(owner, "https://example.com/path/")).isEmpty();
    assertThat(reader.findExactUrl(other, "https://example.com/path")).isEmpty();
  }

  @Test
  void databaseRejectsMismatchedApplicationHash() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into articles(id,account_id,url,url_hash,title,saved_at) values(?,?,?,?,?,?)",
                    UUID.randomUUID(),
                    owner,
                    "https://example.com",
                    new byte[32],
                    "Title",
                    java.sql.Timestamp.from(savedAt)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("articles_url_hash_matches");
  }

  @Test
  void uniqueHashIsPerAccount() {
    insert(owner, "https://example.com/path", null, savedAt);
    assertThatThrownBy(() -> insert(owner, "https://example.com/path", null, savedAt))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("articles_account_url_hash_unique");
    assertThatCode(() -> insert(other, "https://example.com/path", null, savedAt))
        .doesNotThrowAnyException();
  }

  @Test
  void cursorOrdersIdenticalTimestampsByUuidAndExcludesNewerRows() {
    var low = UUID.fromString("00000000-0000-0000-0000-000000000001");
    var high = UUID.fromString("00000000-0000-0000-0000-000000000002");
    insert(low, owner, "https://example.com/low", null, savedAt);
    insert(high, owner, "https://example.com/high", "<p>Content</p>", savedAt);
    insert(other, "https://example.com/private", null, savedAt.plusSeconds(5));
    var first = reader.findNewest(owner, null, 1);
    assertThat(first).extracting(ArticleSummary::id).containsExactly(high);
    assertThat(first.getFirst().hasContent()).isTrue();
    insert(owner, "https://example.com/new", null, savedAt.plusSeconds(10));
    var next = reader.findNewest(owner, new ArticlePosition(savedAt, high), 10);
    assertThat(next).extracting(ArticleSummary::id).containsExactly(low);
    assertThat(next.getFirst().hasContent()).isFalse();
    assertThat(reader.findNewest(owner, new ArticlePosition(savedAt, low), 10)).isEmpty();
  }

  @Test
  void staleJpaUpdateCannotOverwriteCommittedContent() {
    var id = insert(owner, "https://example.com/versioned", "<p>Initial</p>", savedAt);
    try (var first = entityManagers.createEntityManager();
        var second = entityManagers.createEntityManager()) {
      first.getTransaction().begin();
      second.getTransaction().begin();
      var initial1 = first.find(ArticleEntity.class, id);
      var initial2 = second.find(ArticleEntity.class, id);
      initial1.replaceContent("<p>First update</p>");
      first.getTransaction().commit();
      initial2.replaceContent("<p>Stale update</p>");
      assertThatThrownBy(() -> second.getTransaction().commit())
          .isInstanceOf(jakarta.persistence.RollbackException.class)
          .hasCauseInstanceOf(jakarta.persistence.OptimisticLockException.class);
    }
    assertThat(reader.findOwned(owner, id))
        .hasValueSatisfying(
            a -> {
              assertThat(a.content()).isEqualTo("<p>First update</p>");
              assertThat(a.version()).isEqualTo(1);
            });
  }

  @Test
  void unchangedJpaEntityDoesNotAdvanceVersion() {
    var id = insert(owner, "https://example.com/unchanged", "<p>Same</p>", savedAt);
    try (var entityManager = entityManagers.createEntityManager()) {
      entityManager.getTransaction().begin();
      entityManager.find(ArticleEntity.class, id).replaceContent("<p>Same</p>");
      entityManager.getTransaction().commit();
    }
    assertThat(reader.findOwned(owner, id))
        .hasValueSatisfying(a -> assertThat(a.version()).isZero());
  }

  @Test
  void ownershipForeignKeyRejectsUnknownAccount() {
    assertThatThrownBy(
            () -> insert(UUID.randomUUID(), "https://example.com/no-owner", null, savedAt))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void shaIndexSupportsLongUrlsWithoutTruncation() {
    var url = "https://example.com/?query=" + "x".repeat(20000);
    var id = insert(owner, url, null, savedAt);
    assertThat(reader.findExactUrl(owner, url))
        .hasValueSatisfying(a -> assertThat(a.id()).isEqualTo(id));
  }

  @Test
  void simultaneousSavesCannotCreateDuplicateRows() throws Exception {
    var barrier = new java.util.concurrent.CyclicBarrier(2);
    java.util.concurrent.Callable<Boolean> save =
        () -> {
          barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
          try {
            insert(owner, "https://example.com/concurrent", null, savedAt);
            return true;
          } catch (DataIntegrityViolationException duplicate) {
            return false;
          }
        };
    try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var results =
          workers.invokeAll(
              java.util.List.of(save, save), 10, java.util.concurrent.TimeUnit.SECONDS);
      assertThat(
              results.stream()
                  .filter(
                      result -> {
                        try {
                          return result.get();
                        } catch (Exception failure) {
                          throw new AssertionError(failure);
                        }
                      })
                  .count())
          .isEqualTo(1);
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from articles where account_id = ?", Integer.class, owner))
        .isEqualTo(1);
  }

  private void account(UUID id) {
    jdbc.update(
        "insert into accounts(id,created_at) values(?,?)", id, java.sql.Timestamp.from(savedAt));
    categoryFixtures.ensureDefaults(id);
  }

  private UUID insert(UUID account, String url, String content, Instant time) {
    var id = UUID.randomUUID();
    insert(id, account, url, content, time);
    return id;
  }

  private void insert(UUID id, UUID account, String url, String content, Instant time) {
    jdbc.update(
        "insert into articles(id,account_id,url,url_hash,title,content,saved_at) values(?,?,?,?,?,?,?)",
        id,
        account,
        url,
        hasher.sha256(url),
        "Article title",
        content,
        java.sql.Timestamp.from(time));
  }
}
