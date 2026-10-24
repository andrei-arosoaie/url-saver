package com.know.articles.infrastructure.config;

import com.know.accounts.application.TemporaryAccount;
import com.know.articles.application.ArticleFailure;
import com.know.articles.application.CurrentAccount;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityContextAccount implements CurrentAccount {
  private final TemporaryAccount temporaryAccount;

  public UUID requireId() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof UUID accountId)) {
      return temporaryAccount
          .resolve()
          .orElseThrow(() -> new ArticleFailure(401, "UNAUTHENTICATED", "Sign in to continue."));
    }
    return accountId;
  }
}
