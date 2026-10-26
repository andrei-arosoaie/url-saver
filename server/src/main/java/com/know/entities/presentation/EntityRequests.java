package com.know.entities.presentation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.know.entities.application.EntityType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.*;

public final class EntityRequests {
  private EntityRequests() {}

  @Schema(
      requiredProperties = {"type", "title"},
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record CreateEntityRequest(
      EntityType type, String title, String status, Instant completedAt) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static CreateEntityRequest parse(Map<String, Object> fields) {
      keys(fields, "type", "title", "status", "completedAt");
      return new CreateEntityRequest(
          EntityRequests.type(fields),
          requiredText(fields, "title"),
          text(fields, "status"),
          instant(fields, "completedAt"));
    }
  }

  @Schema(
      requiredProperties = {"version"},
      minProperties = 2,
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record EditEntityRequest(
      @Schema(minimum = "0") Long version, String title, String status, Instant completedAt) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static EditEntityRequest parse(Map<String, Object> fields) {
      keys(fields, "version", "title", "status", "completedAt");
      return new EditEntityRequest(
          EntityRequests.version(fields),
          text(fields, "title"),
          text(fields, "status"),
          instant(fields, "completedAt"));
    }
  }

  @Schema(
      requiredProperties = {"type", "title"},
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record AddArticleEntityRequest(EntityType type, String title) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static AddArticleEntityRequest parse(Map<String, Object> fields) {
      keys(fields, "type", "title");
      return new AddArticleEntityRequest(
          EntityRequests.type(fields), requiredText(fields, "title"));
    }
  }

  @Schema(
      requiredProperties = {"version"},
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record AddCompletionRequest(@Schema(minimum = "0") Long version, Instant completedAt) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static AddCompletionRequest parse(Map<String, Object> fields) {
      keys(fields, "version", "completedAt");
      return new AddCompletionRequest(
          EntityRequests.version(fields), instant(fields, "completedAt"));
    }
  }

  @Schema(
      requiredProperties = {"version", "completedAt"},
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record CorrectCompletionRequest(@Schema(minimum = "0") Long version, Instant completedAt) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static CorrectCompletionRequest parse(Map<String, Object> fields) {
      keys(fields, "version", "completedAt");
      if (!fields.containsKey("completedAt"))
        throw new IllegalArgumentException("completedAt is required.");
      return new CorrectCompletionRequest(
          EntityRequests.version(fields), instant(fields, "completedAt"));
    }
  }

  private static void keys(Map<String, Object> fields, String... names) {
    if (!Set.of(names).containsAll(fields.keySet()))
      throw new IllegalArgumentException("Unknown field.");
  }

  private static String text(Map<String, Object> fields, String name) {
    if (!fields.containsKey(name)) return null;
    if (!(fields.get(name) instanceof String value))
      throw new IllegalArgumentException(name + " must be a non-null string.");
    return value;
  }

  private static String requiredText(Map<String, Object> fields, String name) {
    var text = text(fields, name);
    if (text == null) throw new IllegalArgumentException(name + " is required.");
    return text;
  }

  private static EntityType type(Map<String, Object> fields) {
    return EntityType.valueOf(requiredText(fields, "type"));
  }

  private static Long version(Map<String, Object> fields) {
    var value = fields.get("version");
    if (!(value instanceof Integer || value instanceof Long))
      throw new IllegalArgumentException("Integer version required.");
    return ((Number) value).longValue();
  }

  private static Instant instant(Map<String, Object> fields, String name) {
    var value = text(fields, name);
    return value == null ? null : Instant.parse(value);
  }
}
