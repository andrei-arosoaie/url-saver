package com.know.articles.application;

import static org.assertj.core.api.Assertions.*;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class UrlHasherTest {
  private final UrlHasher hasher = new UrlHasher();

  @Test
  void matchesKnownSha256Vector() {
    assertThat(HexFormat.of().formatHex(hasher.sha256("abc")))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }

  @Test
  void preservesExactUrlDifferences() {
    assertThat(hasher.sha256("https://example.com/a"))
        .isNotEqualTo(hasher.sha256("https://example.com/a/"));
    assertThat(hasher.sha256("https://example.com/?a=1&b=2"))
        .isNotEqualTo(hasher.sha256("https://example.com/?b=2&a=1"));
  }

  @Test
  void hashesUnicodeAsUtf8() {
    assertThat(HexFormat.of().formatHex(hasher.sha256("é")))
        .isEqualTo("4a99557e4033c3539de2eb65472017cad5f9557f7a0625a09f1c3f6e2ba69c4c");
  }
}
