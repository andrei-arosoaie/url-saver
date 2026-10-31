package com.know.identification.application;

import com.know.accounts.application.CurrentAccount;
import com.know.articles.application.IdentificationArticles;
import com.know.shared.application.RequestFailure;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdentificationService {
  private final CurrentAccount account;
  private final IdentificationArticles articles;
  private final IdentificationStore store;
  private final IdentificationPolicy policy;
  private final Clock clock;

  @Transactional(readOnly = true)
  public Identification.Status status(UUID article) {
    var id = account.requireId();
    var input = articles.read(id, article);
    var status = store.status(id, article);
    return new Identification.Status(
        status.status(),
        status.reason(),
        status.provider(),
        status.model(),
        input.nameSource().equals("USER") ? java.util.List.of() : status.subjects(),
        input.categorySource(),
        input.nameSource(),
        status.requestedAt(),
        status.completedAt());
  }

  @Transactional
  public Identification.Status request(UUID article) {
    var id = account.requireId();
    articles.read(id, article);
    if (!policy.enabled())
      throw new RequestFailure(503, "IDENTIFICATION_DISABLED", "Identification is disabled.");
    store.enqueue(id, article, clock.instant(), true);
    return status(article);
  }
}
