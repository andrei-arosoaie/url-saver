package com.know.identification.application;

import com.know.articles.application.IdentificationArticles;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdentificationCompletion {
  private final IdentificationStore store;
  private final IdentificationArticles articles;
  private final Clock clock;

  @Transactional
  public void complete(
      Identification.Job job,
      IdentificationArticles.Input input,
      Identification.Result result,
      String main,
      String provider,
      String model) {
    // finish checks the current claim before any article writes; stale workers cannot apply
    // results.
    store.finish(job, "SUCCEEDED", null, provider, model, result.subjects(), clock.instant());
    if (!articles.apply(input, result.category(), main))
      throw new com.know.shared.application.RequestFailure(
          409, "ARTICLE_CHANGED", "Article changed during identification.");
  }
}
