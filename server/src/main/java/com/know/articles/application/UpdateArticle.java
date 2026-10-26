package com.know.articles.application;

public record UpdateArticle(
    Long version,
    String title,
    String content,
    String category,
    String entityName,
    boolean entityNamePresent) {
  public UpdateArticle(Long version, String title, String content) {
    this(version, title, content, null, null, false);
  }
}
