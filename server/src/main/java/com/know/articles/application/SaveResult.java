package com.know.articles.application;

public record SaveResult(Outcome outcome, ArticleSnapshot article) {
  public enum Outcome {
    CREATED,
    ENRICHED,
    UNCHANGED
  }
}
