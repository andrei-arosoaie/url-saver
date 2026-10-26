package com.know.articles.presentation;

import com.know.articles.application.ArticleFailure;
import com.know.articles.application.ArticlePosition;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ArticleCursor {
  public String encode(ArticlePosition position, String scope) {
    return position == null
        ? null
        : com.know.shared.application.ScopedCursor.encode(
            new com.know.shared.application.ScopedCursor.Position(
                position.savedAt(), position.id()),
            scope);
  }

  public ArticlePosition decode(String cursor, String scope) {
    var position = com.know.shared.application.ScopedCursor.decode(cursor, scope);
    return position == null ? null : new ArticlePosition(position.time(), position.id());
  }

  private static final Instant MIN_TIME = Instant.parse("0001-01-01T00:00:00Z");
  private static final Instant MAX_TIME = Instant.parse("9999-12-31T23:59:59.999999Z");

  public String encode(ArticlePosition position) {
    if (position == null) return null;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(
            (position.savedAt() + "|" + position.id()).getBytes(StandardCharsets.UTF_8));
  }

  public ArticlePosition decode(String cursor) {
    if (cursor == null) return null;
    try {
      if (cursor.length() > 256 || !cursor.matches("[A-Za-z0-9_-]+"))
        throw new IllegalArgumentException();
      var parts =
          new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
              .split("\\|", -1);
      if (parts.length != 2) throw new IllegalArgumentException();
      var time = Instant.parse(parts[0]);
      if (time.isBefore(MIN_TIME) || time.isAfter(MAX_TIME)) throw new IllegalArgumentException();
      var position = new ArticlePosition(time, UUID.fromString(parts[1]));
      if (!cursor.equals(encode(position))) throw new IllegalArgumentException();
      return position;
    } catch (IllegalArgumentException | DateTimeParseException exception) {
      throw new ArticleFailure(400, "INVALID_REQUEST", "Invalid article cursor.");
    }
  }
}
