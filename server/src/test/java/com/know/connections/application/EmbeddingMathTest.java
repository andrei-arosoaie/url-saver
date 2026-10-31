package com.know.connections.application;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EmbeddingMathTest {
  @Test
  void normalizesFiniteVectorsWithoutMutatingProviderResponse() {
    float[] source = {3, 4};
    assertThat(EmbeddingMath.normalized(source, 2)).containsExactly(.6f, .8f);
    assertThat(source).containsExactly(3, 4);
    for (float[] invalid :
        new float[][] {{0, 0}, {Float.NaN, 1}, {Float.POSITIVE_INFINITY, 1}, {1}})
      assertThatThrownBy(() -> EmbeddingMath.normalized(invalid, 2))
          .isInstanceOf(EmbeddingFailure.class);
  }

  @Test
  void preservesUnicodeBoundariesAndHashesFullUtf8Text() {
    assertThat(EmbeddingMath.boundary("a😀b", 2)).isEqualTo(1);
    assertThat(EmbeddingMath.boundary("a😀b", 3)).isEqualTo(3);
    assertThat(EmbeddingMath.hex("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    assertThat(EmbeddingMath.hex("😀")).isNotEqualTo(EmbeddingMath.hex("?"));
  }
}
