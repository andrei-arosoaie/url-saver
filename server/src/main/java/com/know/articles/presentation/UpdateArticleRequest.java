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
        "Supply title, content, category and/or entityName. Omission preserves the stored value. Explicit null clears only entityName. URL changes and other null values are rejected.")
public record UpdateArticleRequest(
    @Schema(minimum = "0") Long version,
    String title,
    String content,
    String category,
    @Schema(types = {"string", "null"}) String entityName,
    @Schema(hidden = true) boolean entityNamePresent) {
  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static UpdateArticleRequest fromJson(Map<String, Object> fields) {
    if (!Set.of("version", "title", "content", "category", "entityName")
        .containsAll(fields.keySet())) {
      throw new IllegalArgumentException("Unknown update field.");
    }
    var version = fields.get("version");
    if (!(version instanceof Integer || version instanceof Long)) {
      throw new IllegalArgumentException("An integer version is required.");
    }
    for (var name : Set.of("title", "content", "category")) {
      if (fields.containsKey(name) && !(fields.get(name) instanceof String)) {
        throw new IllegalArgumentException(name + " must be a non-null string.");
      }
    }
    if (fields.get("entityName") != null && !(fields.get("entityName") instanceof String))
      throw new IllegalArgumentException("entityName must be a string or null");
    return new UpdateArticleRequest(
        ((Number) version).longValue(),
        (String) fields.get("title"),
        (String) fields.get("content"),
        (String) fields.get("category"),
        (String) fields.get("entityName"),
        fields.containsKey("entityName"));
  }
}
