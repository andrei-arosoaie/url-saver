package com.know.entities.presentation;

import com.know.entities.application.EntityService;
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
@RequestMapping(value = "/api/v1/articles/{articleId}/entities", produces = "application/json")
@Tag(name = "Article entities")
@RequiredArgsConstructor
public class ArticleEntityController {
  private final EntityService service;
  private final EntityApiMapper mapper;

  @GetMapping
  @ApiResponse(responseCode = "200", description = "Successful owned response.")
  @Operation(
      operationId = "listArticleEntities",
      summary = "Read entities linked to an owned article",
      description =
          "Newest entity createdAt/UUID first. Owner/article-bound cursor; validate parent ownership even for an empty list.")
  public EntityPageResponse<LinkedEntityResponse> list(
      @PathVariable UUID articleId,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String cursor) {
    var page = service.linkedEntities(articleId, limit, cursor);
    return new EntityPageResponse<>(
        page.items().stream().map(mapper::toResponse).toList(), page.nextCursor());
  }

  @PostMapping(consumes = "application/json")
  @Operation(
      operationId = "addArticleEntity",
      summary = "Explicitly add an identified entity",
      description =
          "201 CREATED for a new entity; 200 LINKED or UNCHANGED for exact owner/type/title reuse. Preserve existing status/history. No automatic extraction or metadata-driven relinking.")
  @ApiResponse(
      responseCode = "201",
      description = "Entity created and linked.",
      headers = @Header(name = "Location", schema = @Schema(type = "string")))
  @ApiResponse(
      responseCode = "200",
      description = "Existing entity reused; link created or unchanged.")
  public ResponseEntity<AddEntityResponse> add(
      @PathVariable UUID articleId, @RequestBody AddArticleEntityRequest request) {
    var result = service.addFromArticle(articleId, mapper.toCommand(request));
    var response = mapper.toResponse(result);
    return result.outcome().equals("CREATED")
        ? ResponseEntity.created(URI.create("/api/v1/entities/" + result.entity().id()))
            .body(response)
        : ResponseEntity.ok(response);
  }

  @PutMapping("/{entityId}")
  @Operation(
      operationId = "linkArticleEntity",
      summary = "Link an existing owned entity",
      description =
          "201 for a new pair, 200 for an existing pair. Preserve original linkedAt and both records.")
  @ApiResponse(responseCode = "201", description = "Association created.")
  @ApiResponse(responseCode = "200", description = "Association already exists.")
  public ResponseEntity<EntityLinkResponse> link(
      @PathVariable UUID articleId, @PathVariable UUID entityId) {
    var result = service.link(articleId, entityId);
    return ResponseEntity.status(result.outcome().equals("UNCHANGED") ? 200 : 201)
        .body(mapper.toResponse(result.link()));
  }

  @DeleteMapping("/{entityId}")
  @ApiResponse(responseCode = "204", description = "Association removed or already absent.")
  @Operation(
      operationId = "unlinkArticleEntity",
      summary = "Unlink only this article/entity pair",
      description =
          "Both parents must be owned. Preserve entity, article, history and all other source links. Repeated unlink returns 204. No entity-delete endpoint.")
  public ResponseEntity<Void> unlink(@PathVariable UUID articleId, @PathVariable UUID entityId) {
    service.unlink(articleId, entityId);
    return ResponseEntity.noContent().build();
  }
}
