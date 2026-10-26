package com.know.articles.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.know.articles.application.UrlHashCollisionException;
import com.know.articles.application.UrlHasher;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JpaArticleReaderTest {
  @Test
  void hashMatchNeverAllowsReadingADifferentUrl() {
    var repository = mock(ArticleJpaRepository.class);
    var mapper = mock(ArticlePersistenceMapper.class);
    var hasher = new UrlHasher();
    var accountId = UUID.randomUUID();
    var originalUrl = "https://example.com/original";
    var requestedUrl = "https://example.com/different";
    var existing =
        new ArticleEntity(
            UUID.randomUUID(),
            accountId,
            originalUrl,
            hasher.sha256(originalUrl),
            "Original",
            "<p>Keep this content.</p>",
            Instant.EPOCH);
    when(repository.findByAccountIdAndUrlHash(eq(accountId), any(byte[].class)))
        .thenReturn(Optional.of(existing));
    var reader =
        new JpaArticleReader(
            repository, mapper, hasher, mock(jakarta.persistence.EntityManager.class));
    assertThatThrownBy(() -> reader.findExactUrl(accountId, requestedUrl))
        .isInstanceOf(UrlHashCollisionException.class);
    verifyNoInteractions(mapper);
    assertThat(existing.getContent()).isEqualTo("<p>Keep this content.</p>");
  }
}
