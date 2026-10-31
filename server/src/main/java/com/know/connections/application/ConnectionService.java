package com.know.connections.application;

import static com.know.connections.application.ConnectionModels.*;

import com.know.accounts.application.CurrentAccount;
import com.know.shared.application.*;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConnectionService {
  private final CurrentAccount currentAccount;
  private final ConnectionStore store;
  private final ConnectionProfiles profiles;
  private final ConnectionPolicy policy;
  private final PageLimits limits;
  private final Clock clock;

  public Page list(UUID article, Integer requested, String cursor) {
    UUID owner = currentAccount.requireId();
    store.article(owner, article);
    int limit = requested == null ? limits.defaultLimit() : requested;
    if (limit < 1 || limit > limits.maxLimit())
      throw new RequestFailure(400, "INVALID_REQUEST", "Invalid page size.");
    String scope = ScopedCursor.scope("connections", owner, article);
    var rows = store.related(owner, article, ScopedCursor.decode(cursor, scope), limit + 1);
    boolean more = rows.size() > limit;
    var items = more ? rows.subList(0, limit) : rows;
    var last = items.isEmpty() ? null : items.getLast();
    return new Page(
        items,
        more
            ? ScopedCursor.encode(new ScopedCursor.Position(last.savedAt(), last.id()), scope)
            : null,
        store.progress(
            owner, article, policy.enabled() ? profiles.current() : null, policy, clock.instant()));
  }

  public void retry(UUID article) {
    UUID owner = currentAccount.requireId();
    store.article(owner, article);
    if (!policy.enabled())
      throw new RequestFailure(409, "CONNECTIONS_DISABLED", "Article connections are disabled.");
    store.retry(owner, article, profiles.current(), clock.instant());
  }

  public void remove(UUID article, UUID other) {
    store.remove(currentAccount.requireId(), article, other, clock.instant());
  }
}
