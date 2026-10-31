package com.know.connections.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(requiredProperties = {"items", "nextCursor", "processing"})
public record ConnectionPageResponse(
    List<Item> items,
    @Schema(types = {"string", "null"}) String nextCursor,
    Processing processing) {
  @Schema(
      name = "RelatedArticleResponse",
      requiredProperties = {
        "id",
        "connectionId",
        "title",
        "entityName",
        "category",
        "url",
        "savedAt",
        "sharedEntities",
        "semanticScore",
        "semanticUpdating"
      })
  public record Item(
      UUID id,
      UUID connectionId,
      String title,
      @Schema(types = {"string", "null"}) String entityName,
      String category,
      String url,
      Instant savedAt,
      List<SharedEntity> sharedEntities,
      @Schema(
              types = {"number", "null"},
              minimum = "-1",
              maximum = "1")
          Double semanticScore,
      boolean semanticUpdating) {}

  @Schema(
      name = "ConnectionSharedEntityResponse",
      requiredProperties = {"id", "name"})
  public record SharedEntity(UUID id, String name) {}

  @Schema(
      name = "ConnectionProcessingResponse",
      requiredProperties = {"state", "reason", "nextAttemptAt", "pollIntervalMs"})
  public record Processing(
      @Schema(
              allowableValues = {
                "DISABLED",
                "SKIPPED",
                "QUEUED",
                "PROCESSING",
                "PAUSED",
                "RETRY_PENDING",
                "FAILED",
                "COMPLETE"
              })
          String state,
      @Schema(types = {"string", "null"}) String reason,
      @Schema(
              types = {"string", "null"},
              format = "date-time")
          Instant nextAttemptAt,
      @Schema(minimum = "1") long pollIntervalMs) {}
}
