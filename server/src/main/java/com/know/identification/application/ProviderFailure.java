package com.know.identification.application;

public class ProviderFailure extends RuntimeException {
  private final boolean quotaExhausted;

  public ProviderFailure(boolean quotaExhausted) {
    super(quotaExhausted ? "PROVIDER_QUOTA_EXHAUSTED" : "PROVIDER_UNAVAILABLE");
    this.quotaExhausted = quotaExhausted;
  }

  public boolean quotaExhausted() {
    return quotaExhausted;
  }
}
