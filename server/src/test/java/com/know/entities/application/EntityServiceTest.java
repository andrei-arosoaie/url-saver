package com.know.entities.application;

import static com.know.entities.application.EntityModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.know.accounts.application.CurrentAccount;
import com.know.articles.application.ArticleReader;
import com.know.shared.application.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class EntityServiceTest {
  private final UUID owner = UUID.randomUUID(), id = UUID.randomUUID();
  private final Instant instant = Instant.parse("2026-10-25T18:30:00.123456Z");
  private final EntityStore store = mock(EntityStore.class);
  private final CurrentAccount current = () -> owner;
  private final PageLimits limits = mock(PageLimits.class);
  private final EntityService service =
      new EntityService(
          current,
          store,
          mock(ArticleReader.class),
          limits,
          () -> 1000,
          Clock.fixed(instant, ZoneOffset.UTC));

  @Test
  void completionTransitionUsesInjectedClockAndKeepsTheExactUnmodifiedTitle() {
    var before =
        new Details(id, EntityType.BOOK, " Dune ", "READING", instant.minusSeconds(10), 4, null, 0);
    when(store.find(owner, id)).thenReturn(Optional.of(before));
    when(store.revise(owner, id, " Dune ", "READ", true))
        .thenReturn(
            new Details(id, EntityType.BOOK, " Dune ", "READ", before.createdAt(), 5, instant, 1));
    var result = service.edit(id, new Edit(4L, null, "READ", null));
    verify(store).addCompletion(owner, id, instant, instant);
    verify(store).findExact(owner, EntityType.BOOK, " Dune ");
    assertThat(result.createdAt()).isEqualTo(before.createdAt());
    assertThat(result.lastCompletedAt()).isEqualTo(instant);
  }

  @Test
  void staleOrInvalidStatusChangesCannotCreateHistory() {
    when(store.find(owner, id))
        .thenReturn(
            Optional.of(new Details(id, EntityType.GAME, "Game", "PLAYING", instant, 8, null, 0)));
    assertThatThrownBy(() -> service.edit(id, new Edit(7L, null, "FINISHED", null)))
        .isInstanceOfSatisfying(
            RequestFailure.class, e -> assertThat(e.getReason()).isEqualTo("VERSION_CONFLICT"));
    assertThatThrownBy(() -> service.edit(id, new Edit(8L, null, "READ", null)))
        .isInstanceOfSatisfying(
            RequestFailure.class,
            e -> assertThat(e.getReason()).isEqualTo("INVALID_ENTITY_STATUS"));
    verify(store, never()).addCompletion(any(), any(), any(), any());
    verify(store, never()).revise(any(), any(), any(), any(), anyBoolean());
  }

  @Test
  void validatesUnicodeCodePointLimitWithoutTrimmingOrCountingSurrogateHalves() {
    var title = "🙂".repeat(1000);
    when(store.findExact(owner, EntityType.BOOK, title)).thenReturn(Optional.empty());
    var entity = new Details(id, EntityType.BOOK, title, "TO_READ", instant, 0, null, 0);
    when(store.insert(eq(owner), any(), eq(instant))).thenReturn(entity);
    when(store.find(owner, id)).thenReturn(Optional.of(entity));
    assertThat(service.create(new Create(EntityType.BOOK, title, null, null)).title())
        .isEqualTo(title);
    assertThatThrownBy(() -> service.create(new Create(EntityType.BOOK, title + "🙂", null, null)))
        .isInstanceOf(RequestFailure.class);
    assertThatThrownBy(() -> service.create(new Create(EntityType.BOOK, " \t\n", null, null)))
        .isInstanceOf(RequestFailure.class);
  }

  @Test
  void suppliedTimeOnUnchangedCompletedStatusIsRejectedWithoutAppending() {
    when(store.find(owner, id))
        .thenReturn(
            Optional.of(new Details(id, EntityType.MOVIE, "Film", "SEEN", instant, 1, instant, 1)));
    assertThatThrownBy(() -> service.edit(id, new Edit(1L, null, "SEEN", instant)))
        .isInstanceOf(RequestFailure.class);
    verify(store, never()).addCompletion(any(), any(), any(), any());
  }
}
