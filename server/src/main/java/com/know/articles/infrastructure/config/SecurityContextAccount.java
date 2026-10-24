package com.know.articles.infrastructure.config;

import com.know.articles.application.ArticleFailure;
import com.know.articles.application.CurrentAccount;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextAccount implements CurrentAccount {
  public UUID requireId() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof UUID accountId)) {
      throw new ArticleFailure(401, "UNAUTHENTICATED", "Sign in to continue.");
    }
    return accountId;
  }
}
