package com.know.shared.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

public final class ScopedCursor {
  private ScopedCursor() {}

  public record Position(Instant time, UUID id) {}

  public static String scope(Object... parts) {
    try {
      var hash = MessageDigest.getInstance("SHA-256");
      for (var part : parts) {
        var text = part == null ? "N" : "V" + part;
        hash.update((text.length() + ":" + text).getBytes(StandardCharsets.UTF_8));
      }
      return HexFormat.of().formatHex(hash.digest());
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public static String encode(Position position, String scope) {
    if (position == null) return null;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(
            (position.time() + "|" + position.id() + "|" + scope).getBytes(StandardCharsets.UTF_8));
  }

  public static Position decode(String cursor, String scope) {
    if (cursor == null) return null;
    try {
      if (cursor.length() > 512 || !cursor.matches("[A-Za-z0-9_-]+"))
        throw new IllegalArgumentException();
      var parts =
          new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
              .split("\\|", -1);
      if (parts.length != 3 || !parts[2].equals(scope)) throw new IllegalArgumentException();
      var p = new Position(Instant.parse(parts[0]), UUID.fromString(parts[1]));
      if (p.time().isBefore(Instant.parse("0001-01-01T00:00:00Z"))
          || p.time().isAfter(Instant.parse("9999-12-31T23:59:59.999999Z"))
          || !cursor.equals(encode(p, scope))) throw new IllegalArgumentException();
      return p;
    } catch (RuntimeException e) {
      throw new RequestFailure(400, "INVALID_REQUEST", "Invalid cursor or changed filters.");
    }
  }
}
