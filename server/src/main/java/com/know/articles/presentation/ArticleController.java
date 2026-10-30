package com.know.articles.presentation;

import com.know.articles.application.*;
import com.know.shared.presentation.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/articles", produces = "application/json")
@Tag(name = "Articles")
@ApiResponses({
  @ApiResponse(
      responseCode = "400",
      description = "Invalid request or cursor.",
      content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
  @ApiResponse(
      responseCode = "401",
      description = "Sign-in required.",
      content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
  @ApiResponse(
      responseCode = "500",
      description = "Unexpected failure; hash collisions use URL_HASH_COLLISION.",
      content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
@RequiredArgsConstructor
public class ArticleController {
  private final ArticleCommands commands;
  private final ArticleQueries queries;
  private final ArticleApiMapper mapper;
  private final ArticleCursor cursors;
  private final com.know.accounts.application.CurrentAccount currentAccount;
  private final com.know.shared.application.PageLimits limits;

  @DeleteMapping("/{id}")
  @Operation(
      operationId = "deleteArticle",
      summary = "Permanently delete an owned article",
      description =
          "Expected version required in query. Removes article, entity associations and screenshot metadata; durably queues provider file cleanup. Entities and completion history remain. Client confirms deletion first. Missing/foreign articles return 404; stale version returns 409 VERSION_CONFLICT.")
  @ApiResponse(
      responseCode = "204",
      description = "Article deleted; remote screenshot cleanup queued durably.")
  public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam Long version) {
    commands.delete(id, version);
    return ResponseEntity.noContent().build();
  }

  @GetMapping
  @Operation(
      operationId = "listArticles",
      summary = "List saved article summaries",
      description =
          "Newest first by savedAt descending, then UUID descending. Optional category and literal case-insensitive query over title, URL and entityName; filtering happens before pagination. No HTML or total count. Send nextCursor unchanged with the same filters; null means the end. A cursor grants no account access. Initial pagination defaults are configurable.")
  @ApiResponse(
      responseCode = "200",
      description = "Owned summaries and the next bookmark.",
      content = @Content(schema = @Schema(implementation = ArticlePageResponse.class)))
  public ArticlePageResponse list(
      @Parameter(
              schema =
                  @Schema(
                      type = "integer",
                      format = "int32",
                      minimum = "1",
                      maximum = "100",
                      defaultValue = "20"))
          @RequestParam(required = false)
          Integer limit,
      @Parameter(
              description = "Opaque bookmark from the preceding page.",
              schema = @Schema(maxLength = 256))
          @RequestParam(required = false)
          String cursor,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String query) {
    var scope =
        com.know.shared.application.ScopedCursor.scope(currentAccount.requireId(), category, query);
    var filtered = category != null || query != null;
    var batch =
        queries.list(
            filtered ? cursors.decode(cursor, scope) : cursors.decode(cursor),
            limit == null ? limits.defaultLimit() : limit,
            category,
            query);
    return new ArticlePageResponse(
        batch.items().stream().map(mapper::toResponse).toList(),
        filtered
            ? cursors.encode(batch.nextPosition(), scope)
            : cursors.encode(batch.nextPosition()));
  }

  @GetMapping("/{id}")
  @Operation(
      operationId = "getArticle",
      summary = "Read an owned article",
      description = "Returns sanitized HTML, or null content for a link-only save.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Owned article.",
        content = @Content(schema = @Schema(implementation = ArticleResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Missing article or another account owns it; same response.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public ArticleResponse detail(@PathVariable UUID id) {
    return mapper.toResponse(queries.detail(id));
  }

  @PostMapping(consumes = "application/json")
  @Operation(
      operationId = "saveArticle",
      summary = "Save an article",
      description =
          "Exact URL matching within the current account. Automatically enrich missing content; never overwrite saved text. Configurable content limit defaults to 4 MiB UTF-8 on input and sanitized output; title limit defaults to 1000 Unicode code points. No truncation. Duplicate content requires explicit PATCH confirmation.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "CREATED; Location points to the saved article.",
        headers = @Header(name = "Location", schema = @Schema(type = "string")),
        content = @Content(schema = @Schema(implementation = SaveArticleResponse.class))),
    @ApiResponse(
        responseCode = "200",
        description = "ENRICHED missing content or UNCHANGED existing save.",
        content = @Content(schema = @Schema(implementation = SaveArticleResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "DUPLICATE_URL; existingArticleId identifies the saved article. Nothing overwritten.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "413",
        description = "CONTENT_TOO_LARGE; nothing saved.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public ResponseEntity<SaveArticleResponse> save(@RequestBody SaveArticleRequest request) {
    var result = commands.save(mapper.toCommand(request));
    var response = mapper.toResponse(result);
    return result.outcome() == SaveResult.Outcome.CREATED
        ? ResponseEntity.created(URI.create("/api/v1/articles/" + result.article().id()))
            .body(response)
        : ResponseEntity.ok(response);
  }

  @PatchMapping(value = "/{id}", consumes = "application/json")
  @Operation(
      operationId = "updateArticle",
      summary = "Explicitly update an article",
      description =
          "Send the version read previously and title, content, category and/or entityName. Omitted fields stay unchanged; explicit null clears only entityName. Category must be enabled for the account. Editing entityName never changes entity links. Null/blank content, URL changes and unknown fields are rejected. Version increments only for actual changes; no automatic conflict retry. The same configurable title/content limits apply.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Current article, including version.",
        content = @Content(schema = @Schema(implementation = ArticleResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Missing article or another account owns it; same response.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "VERSION_CONFLICT; reload before another explicit update.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "413",
        description = "CONTENT_TOO_LARGE; no changes committed.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public ArticleResponse update(@PathVariable UUID id, @RequestBody UpdateArticleRequest request) {
    return mapper.toResponse(commands.update(id, mapper.toCommand(request)));
  }
}
