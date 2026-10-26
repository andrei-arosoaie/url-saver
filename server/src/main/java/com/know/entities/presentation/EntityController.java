package com.know.entities.presentation;

import com.know.entities.application.*;
import com.know.entities.application.EntityModels.Filter;
import com.know.entities.presentation.EntityRequests.*;
import com.know.entities.presentation.EntityResponses.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@com.know.shared.presentation.EntityApiErrors
@RequestMapping(value = "/api/v1/entities", produces = "application/json")
@Tag(name = "Entities")
@RequiredArgsConstructor
public class EntityController {
  private final EntityService service;
  private final EntityApiMapper mapper;

  @GetMapping
  @ApiResponse(responseCode = "200", description = "Successful owned response.")
  @Operation(
      operationId = "listEntities",
      summary = "List one entity type",
      description =
          "Newest createdAt/UUID first. Literal case-insensitive title search; status must belong to type. completionYear uses UTC completion history, independent of current status. Cursors are owner/filter-bound. Default 20/max 100; configurable in YAML.")
  public EntityPageResponse<EntitySummaryResponse> list(
      @RequestParam EntityType type,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Integer completionYear,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String cursor) {
    var page = service.list(new Filter(type, query, status, completionYear), limit, cursor);
    return new EntityPageResponse<>(
        page.items().stream().map(mapper::toResponse).toList(), page.nextCursor());
  }

  @PostMapping(consumes = "application/json")
  @ApiResponse(
      responseCode = "201",
      description = "Entity created; Location identifies it.",
      headers = @Header(name = "Location", schema = @Schema(type = "string")))
  @Operation(
      operationId = "createEntity",
      summary = "Create a manual entity",
      description =
          "Preserves exact title; owner/type/title duplicates return 409 DUPLICATE_ENTITY with existingEntityId. Defaults to TO_READ / TO_BE_SEEN / TO_PLAY. Initial completed status records a completion atomically.")
  public ResponseEntity<EntityResponse> create(@RequestBody CreateEntityRequest request) {
    var result = service.create(mapper.toCommand(request));
    return ResponseEntity.created(URI.create("/api/v1/entities/" + result.id()))
        .body(mapper.toResponse(result));
  }

  @GetMapping("/{id}")
  @ApiResponse(responseCode = "200", description = "Successful owned response.")
  @Operation(
      operationId = "getEntity",
      summary = "Read an owned entity",
      description = "Missing and foreign-owned resources both return 404 RESOURCE_NOT_FOUND.")
  public EntityResponse detail(@PathVariable UUID id) {
    return mapper.toResponse(service.detail(id));
  }

  @PatchMapping(value = "/{id}", consumes = "application/json")
  @ApiResponse(responseCode = "200", description = "Successful owned response.")
  @Operation(
      operationId = "updateEntity",
      summary = "Explicitly edit title or status",
      description =
          "Expected JSON version required. Actual completion transitions append history. Repeating completed status does not. No-op leaves version unchanged. Type and creation time are immutable; stale writes return 409.")
  public EntityResponse edit(@PathVariable UUID id, @RequestBody EditEntityRequest request) {
    return mapper.toResponse(service.edit(id, mapper.toCommand(request)));
  }

  @GetMapping("/years")
  @ApiResponse(responseCode = "200", description = "Successful owned response.")
  @Operation(
      operationId = "listCompletionYears",
      summary = "List UTC completion years and counts",
      description =
          "Descending year. Counts distinct entities and all matching completion events; creation dates never determine year grouping.")
  public CompletionYearsResponse years(
      @RequestParam EntityType type,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) String status) {
    return new CompletionYearsResponse(
        service.years(new Filter(type, query, status, null)).stream()
            .map(mapper::toResponse)
            .toList());
  }

  @GetMapping("/{id}/completions")
  @ApiResponse(responseCode = "200", description = "Successful owned response.")
  @Operation(
      operationId = "listCompletions",
      summary = "Read completion history",
      description =
          "Owner/entity-bound cursor; completedAt descending with UUID tie-breaker. Exact timestamps and event creation times remain distinct.")
  public EntityPageResponse<CompletionResponse> completions(
      @PathVariable UUID id,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String cursor) {
    var page = service.completions(id, limit, cursor);
    return new EntityPageResponse<>(
        page.items().stream().map(mapper::toResponse).toList(), page.nextCursor());
  }

  @PostMapping(value = "/{id}/completions", consumes = "application/json")
  @ApiResponse(
      responseCode = "201",
      description = "Completion created; Location identifies the history event.",
      headers = @Header(name = "Location", schema = @Schema(type = "string")))
  @Operation(
      operationId = "addCompletion",
      summary = "Record another completion",
      description =
          "Entity version required. Uses supplied timestamp or injected server UTC clock without a prompt. Always appends a distinct event and sets completed status atomically, including historical events.")
  public ResponseEntity<AddCompletionResponse> addCompletion(
      @PathVariable UUID id, @RequestBody AddCompletionRequest request) {
    var result = service.addCompletion(id, request.version(), request.completedAt());
    return ResponseEntity.created(
            URI.create("/api/v1/entities/" + id + "/completions/" + result.completion().id()))
        .body(mapper.toResponse(result));
  }

  @PatchMapping(value = "/{id}/completions/{eventId}", consumes = "application/json")
  @ApiResponse(responseCode = "200", description = "Successful owned response.")
  @Operation(
      operationId = "correctCompletion",
      summary = "Correct a completion timestamp",
      description =
          "Completion version required. Preserves current status, creation time and other events. Actual history changes increment entity and completion versions; no-op changes neither.")
  public CompletionResponse correct(
      @PathVariable UUID id,
      @PathVariable UUID eventId,
      @RequestBody CorrectCompletionRequest request) {
    return mapper.toResponse(
        service.correctCompletion(id, eventId, request.version(), request.completedAt()));
  }

  @DeleteMapping("/{id}/completions/{eventId}")
  @ApiResponse(responseCode = "204", description = "Only this completion was removed.")
  @Operation(
      operationId = "deleteCompletion",
      summary = "Remove one completion event",
      description =
          "Completion version required in query. Preserves current status even after deleting the last event; derived counts/dates reflect remaining history.")
  public ResponseEntity<Void> delete(
      @PathVariable UUID id, @PathVariable UUID eventId, @RequestParam Long version) {
    service.deleteCompletion(id, eventId, version);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/articles")
  @ApiResponse(responseCode = "200", description = "Successful owned response.")
  @Operation(
      operationId = "listEntityArticles",
      summary = "Read linked source articles",
      description =
          "Owner/entity-bound cursor, savedAt/UUID newest first. Summaries only; manually created entities can have no sources.")
  public EntityPageResponse<SourceArticleResponse> sources(
      @PathVariable UUID id,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String cursor) {
    var page = service.sources(id, limit, cursor);
    return new EntityPageResponse<>(
        page.items().stream().map(mapper::toResponse).toList(), page.nextCursor());
  }
}
