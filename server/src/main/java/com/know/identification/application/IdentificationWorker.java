package com.know.identification.application;

import com.know.articles.application.IdentificationArticles;
import com.know.categories.application.CategoryCatalog;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IdentificationWorker {
  private static final Logger LOG = LoggerFactory.getLogger(IdentificationWorker.class);
  private final IdentificationArticles articles;
  private final CategoryCatalog categories;
  private final IdentificationStore store;
  private final IdentificationPolicy policy;
  private final ArticleText text;
  private final List<IdentificationProvider> providers;
  private final Clock clock;

  /** Processes one leased durable job. No database transaction spans a provider call. */
  public boolean runOne() {
    if (!policy.enabled()) return false;
    var job = store.claim(clock.instant(), policy.jobLease());
    if (job == null) return false;
    try {
      process(job);
    } catch (com.know.articles.application.ArticleFailure failure) {
      finish(job, "FAILED", "ARTICLE_CHANGED_OR_REMOVED", null, null, List.of());
    } catch (com.know.shared.application.RequestFailure failure) {
      if (!failure.getReason().equals("IDENTIFICATION_CLAIM_EXPIRED"))
        finish(job, "FAILED", failure.getReason(), null, null, List.of());
    } catch (RuntimeException failure) {
      // Do not log provider messages, credentials or captured text.
      LOG.warn(
          "Identification processing failed for article {} ({})",
          job.articleId(),
          failure.getClass().getSimpleName());
      finish(job, "FAILED", "IDENTIFICATION_FAILED", null, null, List.of());
    }
    return true;
  }

  private void process(Identification.Job job) {
    var input = articles.read(job.accountId(), job.articleId());
    if (!input.categoryMissing() && !input.nameMissing()) {
      finish(job, "SKIPPED", "NO_MISSING_FIELDS", null, null, List.of());
      return;
    }
    var articleText = text.extract(input.content());
    if (articleText.isBlank()) {
      finish(job, "SKIPPED", "NO_ARTICLE_TEXT", null, null, List.of());
      return;
    }
    var choices =
        categories.list(job.accountId()).stream()
            .filter(c -> c.enabled())
            .map(c -> new Identification.Choice(c.key(), c.displayName()))
            .toList();
    boolean inconclusive = false;
    for (var providerId : policy.providerOrder()) {
      var provider =
          providers.stream().filter(p -> p.id().equals(providerId)).findFirst().orElse(null);
      if (provider == null) continue;
      var permit = store.acquire(providerId, clock.instant(), policy.jobLease());
      if (permit == null) continue;
      try {
        Identification.Result result = null;
        int offset = 0;
        for (int chunk = 0; chunk < policy.maxChunks() && offset < articleText.length(); chunk++) {
          var current = chunk == 0 ? input : articles.read(job.accountId(), job.articleId());
          if (!java.util.Objects.equals(input.content(), current.content())) {
            store.success(permit);
            finish(job, "FAILED", "ARTICLE_CHANGED", null, null, List.of());
            return;
          }
          if (!current.categoryMissing() && !current.nameMissing()) {
            store.success(permit);
            finish(job, "SKIPPED", "NO_MISSING_FIELDS", null, null, List.of());
            return;
          }
          // A bounded overlap retains context at chunk boundaries without growing prompts.
          int end = Math.min(articleText.length(), offset + policy.chunkCharacters());
          if (end < articleText.length() && Character.isHighSurrogate(articleText.charAt(end - 1)))
            end--;
          var request =
              new Identification.Request(
                  articleText.substring(offset, end),
                  choices,
                  current.categoryMissing(),
                  current.nameMissing());
          result = provider.identify(request);
          validate(result, request);
          if (result.sufficient()) break;
          offset = end == articleText.length() ? end : Math.max(offset + 1, end - 200);
          if (offset < articleText.length()
              && offset > 0
              && Character.isLowSurrogate(articleText.charAt(offset))) offset--;
        }
        store.success(permit);
        if (result == null || !result.sufficient()) {
          inconclusive = true;
          // A valid but inconclusive answer is not a transport failure or a quota event.
          // Give the other provider its single opportunity before reporting failure.
          continue;
        }
        var main =
            result.subjects().stream()
                .filter(Identification.Subject::main)
                .map(Identification.Subject::name)
                .findFirst()
                .orElse(null);
        // The store transaction protects the lease and result together with the article write.
        complete(job, input, result, main, provider);
        return;
      } catch (RuntimeException failure) {
        if (failure instanceof com.know.shared.application.RequestFailure) {
          store.success(permit);
          throw failure;
        }
        boolean quota = failure instanceof ProviderFailure p && p.quotaExhausted();
        store.failure(permit, quota, policy, clock.instant());
        LOG.warn(
            "Identification provider {} failed for article {} ({})",
            provider.id(),
            job.articleId(),
            quota ? "quota exhausted" : "request failed");
      }
    }
    finish(
        job,
        "FAILED",
        inconclusive ? "INSUFFICIENT_EVIDENCE" : "PROVIDERS_UNAVAILABLE",
        null,
        null,
        List.of());
  }

  private final IdentificationCompletion completion;

  private void complete(
      Identification.Job job,
      IdentificationArticles.Input input,
      Identification.Result result,
      String main,
      IdentificationProvider provider) {
    completion.complete(job, input, result, main, provider.id(), provider.model());
  }

  private void validate(Identification.Result result, Identification.Request request) {
    if (result == null || result.subjects() == null || result.subjects().size() > 20)
      throw new ProviderFailure(false);
    if (result.category() != null
        && request.categories().stream().noneMatch(c -> c.key().equals(result.category())))
      throw new ProviderFailure(false);
    if (result.sufficient() && request.categoryNeeded() && result.category() == null)
      throw new ProviderFailure(false);
    if (!result.subjects().isEmpty()
        && result.subjects().stream().filter(Identification.Subject::main).count() != 1)
      throw new ProviderFailure(false);
    var names = new java.util.HashSet<String>();
    for (var subject : result.subjects()) {
      if (subject == null
          || subject.name() == null
          || subject.name().isBlank()
          || subject.name().codePointCount(0, subject.name().length()) > policy.maxNameCodePoints()
          || !names.add(subject.name().strip().toLowerCase(Locale.ROOT)))
        throw new ProviderFailure(false);
    }
  }

  private void finish(
      Identification.Job job,
      String status,
      String reason,
      String provider,
      String model,
      List<Identification.Subject> subjects) {
    try {
      store.finish(job, status, reason, provider, model, subjects, clock.instant());
    } catch (com.know.shared.application.RequestFailure failure) {
      // Deletion or another worker owning the lease is a normal cancellation, not a retry.
      if (!failure.getReason().equals("IDENTIFICATION_CLAIM_EXPIRED")) throw failure;
    }
  }
}
