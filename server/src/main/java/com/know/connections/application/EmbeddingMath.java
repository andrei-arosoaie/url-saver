package com.know.connections.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class EmbeddingMath {
  private EmbeddingMath() {}

  public static byte[] hash(String text) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public static String hex(String value) {
    return HexFormat.of().formatHex(hash(value));
  }

  public static float[] normalized(float[] source, int dimensions) {
    if (source == null || source.length != dimensions) throw new EmbeddingFailure(502, null);
    double sum = 0;
    for (float v : source) {
      if (!Float.isFinite(v)) throw new EmbeddingFailure(502, null);
      sum += (double) v * v;
    }
    if (sum == 0 || !Double.isFinite(sum)) throw new EmbeddingFailure(502, null);
    double norm = Math.sqrt(sum);
    var result = source.clone();
    for (int i = 0; i < result.length; i++) result[i] /= norm;
    return result;
  }

  public static int boundary(String text, int index) {
    if (index > 0 && index < text.length() && Character.isLowSurrogate(text.charAt(index)))
      return index - 1;
    return index;
  }
}
