package com.know.articles.application;

public class UrlHashCollisionException extends RuntimeException {
  public UrlHashCollisionException() {
    super("URL hash collision; existing article was not changed");
  }
}
