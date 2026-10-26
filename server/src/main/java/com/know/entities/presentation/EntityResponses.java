package com.know.entities.presentation;

import com.know.entities.application.EntityType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.*;

public final class EntityResponses {
  private EntityResponses() {}

  @Schema(
      requiredProperties = {
        "id",
        "type",
        "title",
        "status",
        "createdAt",
        "version",
        "lastCompletedAt",
        "totalCompletionCount"
      })
  public record EntityResponse(
      UUID id,
      EntityType type,
      String title,
      String status,
      Instant createdAt,
      long version,
      @Schema(
              types = {"string", "null"},
              format = "date-time")
          Instant lastCompletedAt,
      long totalCompletionCount) {}

  @Schema(
      requiredProperties = {
        "id",
        "type",
        "title",
        "status",
        "createdAt",
        "version",
        "lastCompletedAt",
        "totalCompletionCount",
        "completionCount",
        "latestMatchingCompletionAt"
      })
  public record EntitySummaryResponse(
      UUID id,
      EntityType type,
      String title,
      String status,
      Instant createdAt,
      long version,
      @Schema(
              types = {"string", "null"},
              format = "date-time")
          Instant lastCompletedAt,
      long totalCompletionCount,
      long completionCount,
      @Schema(
              types = {"string", "null"},
              format = "date-time")
          Instant latestMatchingCompletionAt) {}

  @Schema(requiredProperties = {"id", "entityId", "completedAt", "createdAt", "version"})
  public record CompletionResponse(
      UUID id, UUID entityId, Instant completedAt, Instant createdAt, long version) {}

  @Schema(
      requiredProperties = {
        "id",
        "type",
        "title",
        "status",
        "createdAt",
        "version",
        "lastCompletedAt",
        "totalCompletionCount",
        "linkedAt"
      })
  public record LinkedEntityResponse(
      UUID id,
      EntityType type,
      String title,
      String status,
      Instant createdAt,
      long version,
      @Schema(
              types = {"string", "null"},
              format = "date-time")
          Instant lastCompletedAt,
      long totalCompletionCount,
      Instant linkedAt) {}

  @Schema(requiredProperties = {"articleId", "entityId", "linkedAt"})
  public record EntityLinkResponse(UUID articleId, UUID entityId, Instant linkedAt) {}

  @Schema(requiredProperties = {"outcome", "entity", "link"})
  public record AddEntityResponse(
      @Schema(allowableValues = {"CREATED", "LINKED", "UNCHANGED"}) String outcome,
      EntityResponse entity,
      EntityLinkResponse link) {}

  @Schema(requiredProperties = {"entity", "completion"})
  public record AddCompletionResponse(EntityResponse entity, CompletionResponse completion) {}

  @Schema(requiredProperties = {"year", "entityCount", "completionCount"})
  public record CompletionYearResponse(int year, long entityCount, long completionCount) {}

  @Schema(requiredProperties = {"years"})
  public record CompletionYearsResponse(List<CompletionYearResponse> years) {}

  @Schema(requiredProperties = {"items", "nextCursor"})
  public record EntityPageResponse<T>(
      List<T> items, @Schema(types = {"string", "null"}) String nextCursor) {}

  @Schema(
      requiredProperties = {
        "id",
        "url",
        "title",
        "savedAt",
        "version",
        "hasContent",
        "category",
        "entityName"
      })
  public record SourceArticleResponse(
      UUID id,
      String url,
      String title,
      Instant savedAt,
      long version,
      boolean hasContent,
      String category,
      @Schema(types = {"string", "null"}) String entityName) {}
}
