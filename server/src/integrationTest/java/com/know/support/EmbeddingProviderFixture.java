package com.know.support;

import com.know.connections.application.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Controlled embedding boundary: tests never transmit captured text to Google. */
public class EmbeddingProviderFixture implements EmbeddingProvider {
  public final List<String> embedded = new CopyOnWriteArrayList<>();
  public final AtomicInteger failures = new AtomicInteger();
  public volatile int status = 503;
  public volatile Runnable beforeEmbedding = () -> {};
  public volatile Runnable beforeCounting = () -> {};

  public void reset() {
    embedded.clear();
    failures.set(0);
    status = 503;
    beforeEmbedding = () -> {};
    beforeCounting = () -> {};
  }

  public int countTokens(String text) {
    beforeCounting.run();
    return Math.max(1, text.codePointCount(0, text.length()) / 3);
  }

  public float[] embed(String text, int dimensions) {
    beforeEmbedding.run();
    if (failures.getAndUpdate(n -> Math.max(0, n - 1)) > 0)
      throw new EmbeddingFailure(status, null);
    embedded.add(text);
    var result = new float[dimensions];
    result[text.contains("unrelated") ? 1 : 0] = 1;
    return result;
  }
}
