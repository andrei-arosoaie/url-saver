package com.know.identification.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(
    requiredProperties = {
      "status",
      "reason",
      "provider",
      "model",
      "subjects",
      "categorySource",
      "nameSource",
      "requestedAt",
      "completedAt"
    })
public record IdentificationResponse(
    @Schema(
            allowableValues = {
              "NOT_REQUESTED",
              "PENDING",
              "RUNNING",
              "SUCCEEDED",
              "FAILED",
              "SKIPPED"
            })
        String status,
    @Schema(types = {"string", "null"}) String reason,
    @Schema(types = {"string", "null"}) String provider,
    @Schema(types = {"string", "null"}) String model,
    List<SubjectResponse> subjects,
    @Schema(allowableValues = {"DEFAULT", "USER", "AI"}) String categorySource,
    @Schema(allowableValues = {"DEFAULT", "USER", "AI"}) String nameSource,
    @Schema(
            types = {"string", "null"},
            format = "date-time")
        Instant requestedAt,
    @Schema(
            types = {"string", "null"},
            format = "date-time")
        Instant completedAt) {
  @Schema(requiredProperties = {"name", "main"})
  public record SubjectResponse(String name, boolean main) {}
}
