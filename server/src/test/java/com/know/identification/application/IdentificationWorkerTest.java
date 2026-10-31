package com.know.identification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.know.articles.application.IdentificationArticles;
import com.know.categories.application.Category;
import com.know.categories.application.CategoryCatalog;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentificationWorkerTest {
  private final IdentificationArticles articles = mock(IdentificationArticles.class);
  private final CategoryCatalog categories = mock(CategoryCatalog.class);
  private final IdentificationStore store = mock(IdentificationStore.class);
  private final IdentificationCompletion completion = mock(IdentificationCompletion.class);
  private final IdentificationProvider first = mock(IdentificationProvider.class);
  private final IdentificationProvider second = mock(IdentificationProvider.class);
  private final Identification.Job job =
      new Identification.Job(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
  private final Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
  private final IdentificationPolicy policy =
      new IdentificationPolicy(
          true,
          List.of("openrouter", "groq"),
          3,
          Duration.ofDays(1),
          Duration.ofMinutes(10),
          6000,
          8,
          1000);

  private IdentificationWorker worker(String categorySource, String nameSource) {
    when(store.claim(any(), any())).thenReturn(job);
    when(articles.read(job.accountId(), job.articleId()))
        .thenReturn(
            new IdentificationArticles.Input(
                job.accountId(),
                job.articleId(),
                "Article text",
                "OTHER",
                null,
                categorySource,
                nameSource));
    when(categories.list(job.accountId()))
        .thenReturn(
            List.of(
                new Category("AI", "AI", "#123456", true, true, Instant.EPOCH, 0, 0),
                new Category("BOOKS", "Books", "#123456", true, true, Instant.EPOCH, 0, 0)));
    when(first.id()).thenReturn("openrouter");
    when(second.id()).thenReturn("groq");
    when(first.model()).thenReturn("openrouter/free");
    when(second.model()).thenReturn("test-free");
    when(store.acquire(anyString(), any(), any()))
        .thenAnswer(
            call -> new Identification.ProviderPermit(call.getArgument(0), UUID.randomUUID()));
    return new IdentificationWorker(
        articles,
        categories,
        store,
        policy,
        html -> html,
        List.of(first, second),
        clock,
        completion);
  }

  @Test
  void multipleMainSubjectsAreRejectedAndFallbackIsUsed() {
    var worker = worker("DEFAULT", "DEFAULT");
    when(first.identify(any()))
        .thenReturn(
            new Identification.Result(
                true,
                "BOOKS",
                List.of(
                    new Identification.Subject("A", true), new Identification.Subject("B", true))));
    var valid =
        new Identification.Result(true, "AI", List.of(new Identification.Subject("Topic", true)));
    when(second.identify(any())).thenReturn(valid);
    assertThat(worker.runOne()).isTrue();
    verify(store).failure(any(), eq(false), eq(policy), eq(Instant.EPOCH));
    verify(completion)
        .complete(eq(job), any(), eq(valid), eq("Topic"), eq("groq"), eq("test-free"));
  }

  @Test
  void caseInsensitiveDuplicateSubjectsAreRejected() {
    var worker = worker("DEFAULT", "DEFAULT");
    when(first.identify(any()))
        .thenReturn(
            new Identification.Result(
                true,
                "BOOKS",
                List.of(
                    new Identification.Subject("1984", true),
                    new Identification.Subject(" 1984 ", false))));
    when(second.identify(any())).thenThrow(new ProviderFailure(false));
    worker.runOne();
    verifyNoInteractions(completion);
    verify(store)
        .finish(
            eq(job),
            eq("FAILED"),
            eq("PROVIDERS_UNAVAILABLE"),
            isNull(),
            isNull(),
            eq(List.of()),
            eq(Instant.EPOCH));
  }

  @Test
  void bothUserFieldsSkipWithoutCallingAnyProvider() {
    var worker = worker("USER", "USER");
    worker.runOne();
    verify(first, never()).identify(any());
    verify(second, never()).identify(any());
    verify(store)
        .finish(
            eq(job),
            eq("SKIPPED"),
            eq("NO_MISSING_FIELDS"),
            isNull(),
            isNull(),
            eq(List.of()),
            eq(Instant.EPOCH));
  }

  @Test
  void coolingDownProvidersAreNotContacted() {
    var worker = worker("DEFAULT", "DEFAULT");
    when(store.acquire(anyString(), any(), any())).thenReturn(null);
    worker.runOne();
    verify(first, never()).identify(any());
    verify(second, never()).identify(any());
    verifyNoInteractions(completion);
  }
}
