package com.know.articles.presentation;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.Set;

@Schema(
    minProperties = 2,
    requiredProperties = {"version"},
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
    description =
        "Supply title and/or content; omission preserves the stored value. Null values and URL changes are rejected.")
public record UpdateArticleRequest(
    @Schema(minimum = "0") Long version, String title, String content) {
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
