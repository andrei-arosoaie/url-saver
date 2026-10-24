package com.know.articles.presentation;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Map;
import java.util.Set;

public record UpdateArticleRequest(Long version, String title, String content) {
  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static UpdateArticleRequest fromJson(Map<String, Object> fields) {
    if (!Set.of("version", "title", "content").containsAll(fields.keySet())) {
      throw new IllegalArgumentException("Unknown update field.");
    }
    var version = fields.get("version");
    if (!(version instanceof Integer || version instanceof Long)) {
      throw new IllegalArgumentException("An integer version is required.");
    }
    for (var name : Set.of("title", "content")) {
      if (fields.containsKey(name) && !(fields.get(name) instanceof String)) {
        throw new IllegalArgumentException(name + " must be a non-null string.");
      }
    }
    return new UpdateArticleRequest(
        ((Number) version).longValue(),
        (String) fields.get("title"),
        (String) fields.get("content"));
  }
}
