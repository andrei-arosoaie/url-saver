package com.know.connections.presentation;

import com.know.connections.application.ConnectionService;
import com.know.shared.presentation.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/articles/{articleId}/connections")
@RequiredArgsConstructor
@ApiResponses({
  @ApiResponse(
      responseCode = "400",
      description = "Invalid request or cursor",
      content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
  @ApiResponse(
      responseCode = "404",
      description = "An article is missing or belongs to another account",
      content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class ConnectionController {
  private final ConnectionService service;
  private final ConnectionApiMapper mapper;

  @GetMapping
  @ApiResponse(
      responseCode = "200",
      description = "Related articles, next cursor and current processing state",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = ConnectionPageResponse.class)))
  @Operation(
      operationId = "listArticleConnections",
      summary = "Related articles with reasons and processing state",
      description =
          "Newest saved article first, UUID descending on ties. The cursor is scoped to this account and source article. Existing results remain available while work is queued, paused or failed. No total result cap; load nextCursor until null.")
  public ConnectionPageResponse list(
      @PathVariable UUID articleId,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String cursor) {
    return mapper.toResponse(service.list(articleId, limit, cursor));
  }

  @PostMapping("/retry")
  @Operation(
      operationId = "retryArticleConnections",
      summary = "Retry failed connection work within shared provider limits")
  @ApiResponse(
      responseCode = "202",
      description = "Failed work requeued; existing results are preserved")
  @ApiResponse(
      responseCode = "409",
      description = "CONNECTIONS_DISABLED",
      content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  public ResponseEntity<Void> retry(@PathVariable UUID articleId) {
    service.retry(articleId);
    return ResponseEntity.accepted().build();
  }

  @DeleteMapping("/{relatedArticleId}")
  @Operation(
      operationId = "removeArticleConnection",
      summary = "Permanently exclude this article pair in both directions",
      description =
          "Idempotent. Persists an exclusion even during generation, so future updates and backfill cannot recreate the pair. Neither article is deleted.")
  @ApiResponse(responseCode = "204", description = "Pair excluded on both sides")
  public ResponseEntity<Void> remove(
      @PathVariable UUID articleId, @PathVariable UUID relatedArticleId) {
    service.remove(articleId, relatedArticleId);
    return ResponseEntity.noContent().build();
  }
}
