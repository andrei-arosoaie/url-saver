package com.know.connections.application;

public interface EmbeddingProvider {
  int countTokens(String text);

  float[] embed(String text, int dimensions);
}
