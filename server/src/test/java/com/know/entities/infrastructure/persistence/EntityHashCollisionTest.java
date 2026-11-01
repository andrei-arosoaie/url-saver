package com.know.entities.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.know.entities.application.EntityType;
import com.know.shared.application.RequestFailure;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class EntityHashCollisionTest {
  @Test
  void neverReusesAnotherFullTitleEvenWhenTheDigestLookupMatches() {
    var owner = UUID.randomUUID();
    var repository = mock(EntityRepository.class);
    when(repository.findByAccountIdAndTypeAndTitleHash(eq(owner), eq(EntityType.BOOK), any()))
        .thenReturn(
            Optional.of(
                new TrackedEntity(
                    owner,
                    EntityType.BOOK,
                    "A different full title",
                    new byte[32],
                    "TO_READ",
                    Instant.now())));
    var store =
        new JpaEntityStore(
            repository,
            mock(CompletionRepository.class),
            mock(LinkRepository.class),
            mock(EntityPersistenceMapper.class),
            mock(EntityManager.class),
            java.time.Clock.systemUTC());
    assertThatThrownBy(() -> store.findExact(owner, EntityType.BOOK, "Requested title"))
        .isInstanceOfSatisfying(
            RequestFailure.class,
            error -> {
              assertThat(error.getStatus()).isEqualTo(500);
              assertThat(error.getReason()).isEqualTo("ENTITY_TITLE_HASH_COLLISION");
              assertThat(error.getExistingEntityId()).isNull();
            });
  }
}
