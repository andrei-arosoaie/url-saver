package com.know.connections.application;

import java.time.Instant;

public class EmbeddingFailure extends RuntimeException {
  private final int status;
  private final Instant retryAfter;

  public EmbeddingFailure(int status, Instant retryAfter) {
    super(status == 429 ? "PROVIDER_RATE_LIMITED" : "EMBEDDING_PROVIDER_FAILED");
    this.status = status;
    this.retryAfter = retryAfter;
  }

  public int status() {
    return status;
  }

  public Instant retryAfter() {
    return retryAfter;
  }

  public boolean transientFailure() {
    return status == 0 || status == 408 || status >= 500;
  }
}
