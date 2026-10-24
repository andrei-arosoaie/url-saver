package com.know.articles.presentation;

import static org.assertj.core.api.Assertions.*;

import com.know.articles.application.ArticleFailure;
import com.know.articles.application.ArticlePosition;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ArticleCursorTest {
  private final ArticleCursor codec = new ArticleCursor();

  @Test
  void preservesMicrosecondPositionAndUnsignedUuid() {
    var position =
        new ArticlePosition(
            Instant.parse("2026-10-24T10:30:40.123456Z"),
            UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));
    assertThat(codec.decode(codec.encode(position))).isEqualTo(position);
    assertThat(codec.decode(null)).isNull();
    assertThat(codec.encode(null)).isNull();
  }

  @Test
  void rejectsMalformedNoncanonicalAndOutOfRangeCursors() {
    for (var cursor :
        new String[] {
          "",
          "%%%",
          "a".repeat(257),
          encoded("bad-time|00000000-0000-0000-0000-000000000001"),
          encoded("2026-10-24T10:00:00Z|bad-id"),
          encoded("2026-10-24T10:00:00Z|1-1-1-1-1"),
          encoded("+999999999-12-31T23:59:59Z|00000000-0000-0000-0000-000000000001"),
          encoded("2026-10-24T10:00:00Z|00000000-0000-0000-0000-000000000001|extra")
        }) {
      assertThatThrownBy(() -> codec.decode(cursor))
          .isInstanceOfSatisfying(
              ArticleFailure.class, error -> assertThat(error.getStatus()).isEqualTo(400));
    }
  }

  private String encoded(String text) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(text.getBytes(StandardCharsets.UTF_8));
  }
}
