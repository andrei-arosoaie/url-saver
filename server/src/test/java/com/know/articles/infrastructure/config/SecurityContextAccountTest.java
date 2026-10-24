package com.know.articles.infrastructure.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.know.accounts.application.TemporaryAccount;
import com.know.articles.application.ArticleFailure;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityContextAccountTest {
  private final TemporaryAccount temporary = mock(TemporaryAccount.class);
  private final SecurityContextAccount current = new SecurityContextAccount(temporary);

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void usesConfiguredTemporaryAccountWithoutAuthentication() {
    var id = UUID.randomUUID();
    when(temporary.resolve()).thenReturn(Optional.of(id));
    assertThat(current.requireId()).isEqualTo(id);
  }

  @Test
  void rejectsUnauthenticatedRequestsWhenTemporaryModeIsDisabled() {
    when(temporary.resolve()).thenReturn(Optional.empty());
    assertThatThrownBy(current::requireId).isInstanceOf(ArticleFailure.class);
  }

  @Test
  void authenticatedAccountTakesPrecedence() {
    var id = UUID.randomUUID();
    SecurityContextHolder.getContext()
        .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(id, null, List.of()));
    assertThat(current.requireId()).isEqualTo(id);
    verifyNoInteractions(temporary);
  }
}
