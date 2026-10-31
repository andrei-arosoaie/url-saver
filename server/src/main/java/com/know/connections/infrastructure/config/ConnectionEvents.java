package com.know.connections.infrastructure.config;

import com.know.connections.application.*;
import com.know.shared.application.*;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConnectionEvents {
  private final ConnectionStore store;
  private final ConnectionProfiles profiles;
  private final ConnectionPolicy policy;
  private final Clock clock;

  @EventListener
  public void captured(ArticleCaptured event) {
    change(event.accountId(), event.articleId());
  }

  @EventListener
  public void content(ArticleContentChanged event) {
    change(event.accountId(), event.articleId());
  }

  @EventListener
  public void entities(ArticleEntitiesChanged event) {
    change(event.accountId(), event.articleId());
  }

  @EventListener
  public void deleting(ArticleDeleting event) {
    store.delete(event.accountId(), event.articleId());
  }

  private void change(UUID owner, UUID article) {
    store.changed(
        owner, article, policy.enabled() ? profiles.current() : null, clock.instant(), true);
  }
}
