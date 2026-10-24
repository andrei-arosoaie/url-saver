package com.know.articles.application;

public interface ArticleContent {
  String sanitize(String html);

  void validateTitle(String title);

  void validateUrl(String url);
}
