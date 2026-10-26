package com.know.articles.presentation;

import static org.assertj.core.api.Assertions.*;

import com.know.articles.application.ArticlePosition;
import com.know.shared.application.RequestFailure;
import com.know.shared.application.ScopedCursor;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScopedArticleCursorTest {
  @Test
  void rejectsFilterOrAccountChangesAndDistinguishesMissingFromLiteralNull() {
    var cursors = new ArticleCursor();
    var owner = UUID.randomUUID();
    var scope = ScopedCursor.scope(owner, "BOOKS", null);
    var position =
        new ArticlePosition(Instant.parse("2026-10-25T12:34:56.123456Z"), UUID.randomUUID());
    var cursor = cursors.encode(position, scope);
    assertThat(cursors.decode(cursor, scope)).isEqualTo(position);
    for (var different :
        new String[] {
          ScopedCursor.scope(owner, "BOOKS", "null"),
          ScopedCursor.scope(owner, "MOVIES", null),
          ScopedCursor.scope(UUID.randomUUID(), "BOOKS", null)
        }) {
      assertThatThrownBy(() -> cursors.decode(cursor, different))
          .isInstanceOf(RequestFailure.class);
    }
    assertThatThrownBy(() -> cursors.decode(cursor + "=", scope))
        .isInstanceOf(RequestFailure.class);
  }
}
