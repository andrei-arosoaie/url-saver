package com.know.identification.infrastructure.config;

import com.know.identification.application.IdentificationPolicy;
import com.know.identification.application.IdentificationStore;
import com.know.shared.application.ArticleCaptured;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IdentificationCaptureListener {
  private final IdentificationStore store;
  private final IdentificationPolicy policy;
  private final Clock clock;

  @EventListener
  public void captured(ArticleCaptured event) {
    if (policy.enabled())
      store.enqueue(event.accountId(), event.articleId(), clock.instant(), false);
  }
}
