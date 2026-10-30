package com.know.screenshots.presentation;

import com.know.screenshots.application.ScreenshotService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.mapstruct.Mapper;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/articles/{articleId}/screenshot", produces = "application/json")
public class ScreenshotController {
  private final ScreenshotService service;
  private final ScreenshotMapper mapper;

  @GetMapping
  @Operation(
      operationId = "getArticleScreenshot",
      summary = "Read an owned screenshot and temporary private download URL")
  @ApiResponse(
      responseCode = "200",
      description = "AVAILABLE or MISSING; signed URLs expire after the configured lifetime.",
      content = @Content(schema = @Schema(implementation = ScreenshotResponse.class)))
  public ResponseEntity<ScreenshotResponse> read(
      @PathVariable UUID articleId, @RequestParam(defaultValue = "false") boolean refresh) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(mapper.response(service.read(articleId, refresh)));
  }

  @PutMapping(consumes = "multipart/form-data")
  @Operation(
      operationId = "uploadArticleScreenshot",
      summary = "Upload native PNG separately from an already saved article",
      description =
          "metadata is JSON with captureId and expectedVersion. file is a native PNG; omit to record an unavailable capture. Backend transforms to its configured JPEG/PNG, retaining native dimensions. Reuse captureId and identical bytes for retries. Ownership, version and capture checks prevent automatic overwrite by stale requests. Article saves are independent.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "AVAILABLE or explicitly MISSING",
        content = @Content(schema = @Schema(implementation = ScreenshotResponse.class))),
    @ApiResponse(responseCode = "400", description = "Invalid metadata or PNG"),
    @ApiResponse(responseCode = "404", description = "Missing or foreign article"),
    @ApiResponse(
        responseCode = "409",
        description = "Stale version, superseded capture or changed bytes"),
    @ApiResponse(
        responseCode = "413",
        description = "Upload, stored image or decoded pixels exceed configured limits"),
    @ApiResponse(
        responseCode = "503",
        description = "Retryable screenshot upload/provider failure; article remains saved")
  })
  public ResponseEntity<ScreenshotResponse> upload(
      @PathVariable UUID articleId,
      @RequestPart("metadata") ScreenshotRequest metadata,
      @RequestPart(value = "file", required = false) MultipartFile file)
      throws java.io.IOException {
    if (metadata.expectedVersion() == null)
      throw new com.know.shared.application.RequestFailure(
          400, "INVALID_REQUEST", "Screenshot version is required.");
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            mapper.response(
                service.upload(
                    articleId,
                    metadata.captureId(),
                    metadata.expectedVersion(),
                    file == null ? null : file.getBytes())));
  }

  @Schema(
      requiredProperties = {"captureId", "expectedVersion"},
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record ScreenshotRequest(UUID captureId, @Schema(minimum = "0") Long expectedVersion) {}

  @Schema(requiredProperties = {"status", "version"})
  public record ScreenshotResponse(
      String status,
      long version,
      @Schema(types = {"string", "null"}) String url,
      @Schema(
              types = {"string", "null"},
              format = "date-time")
          Instant expiresAt,
      @Schema(types = {"string", "null"}) String contentType,
      @Schema(
              types = {"integer", "null"},
              format = "int64")
          Long byteSize,
      @Schema(
              types = {"integer", "null"},
              format = "int32")
          Integer width,
      @Schema(
              types = {"integer", "null"},
              format = "int32")
          Integer height) {}

  @Mapper(componentModel = "spring")
  public interface ScreenshotMapper {
    ScreenshotResponse response(ScreenshotService.View view);
  }
}
