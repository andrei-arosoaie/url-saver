package com.know.categories.presentation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.know.accounts.application.CurrentAccount;
import com.know.categories.application.CategoryCatalog;
import com.know.categories.application.CategoryManagement;
import com.know.shared.application.RequestFailure;
import com.know.shared.presentation.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.mapstruct.Mapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Categories")
@ApiResponses({
  @ApiResponse(
      responseCode = "400",
      description = "INVALID_REQUEST; invalid name, color, version or JSON fields.",
      content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
  @ApiResponse(
      responseCode = "401",
      description = "UNAUTHENTICATED.",
      content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
  @ApiResponse(
      responseCode = "404",
      description = "RESOURCE_NOT_FOUND for missing or foreign category.",
      content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
  @ApiResponse(
      responseCode = "409",
      description =
          "VERSION_CONFLICT, DUPLICATE_CATEGORY_NAME, CATEGORY_IN_USE, CATEGORY_REQUIRED or DEFAULT_CATEGORY_PROTECTED. Nothing changed.",
      content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
@RequiredArgsConstructor
public class CategoryController {
  private final CurrentAccount current;
  private final CategoryCatalog catalog;
  private final CategoryApiMapper mapper;
  private final CategoryManagement management;

  public record CategoryResponse(
      String key,
      String displayName,
      String color,
      boolean isDefault,
      boolean enabled,
      Instant createdAt,
      long version,
      long articleCount) {}

  @Schema(
      requiredProperties = {"displayName", "color"},
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record CreateCategoryRequest(String displayName, String color) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static CreateCategoryRequest parse(Map<String, Object> fields) {
      if (!fields.keySet().equals(Set.of("displayName", "color"))
          || !(fields.get("displayName") instanceof String name)
          || !(fields.get("color") instanceof String color))
        throw new RequestFailure(
            400, "INVALID_REQUEST", "Supply a category name and #RRGGBB color.");
      return new CreateCategoryRequest(name, color);
    }
  }

  @Schema(
      requiredProperties = {"version"},
      minProperties = 2,
      additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record EditCategoryRequest(
      Long version, String displayName, String color, Boolean enabled) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static EditCategoryRequest parse(Map<String, Object> fields) {
      if (!Set.of("version", "displayName", "color", "enabled").containsAll(fields.keySet())
          || fields.values().stream().anyMatch(java.util.Objects::isNull)
          || !(fields.get("version") instanceof Integer || fields.get("version") instanceof Long)
          || (fields.containsKey("displayName") && !(fields.get("displayName") instanceof String))
          || (fields.containsKey("color") && !(fields.get("color") instanceof String))
          || (fields.containsKey("enabled") && !(fields.get("enabled") instanceof Boolean)))
        throw new RequestFailure(400, "INVALID_REQUEST", "Invalid category fields.");
      return new EditCategoryRequest(
          ((Number) fields.get("version")).longValue(),
          (String) fields.get("displayName"),
          (String) fields.get("color"),
          (Boolean) fields.get("enabled"));
    }
  }

  public record CatalogResponse(List<CategoryResponse> items) {}

  @GetMapping
  @Operation(
      operationId = "listCategories",
      summary = "List owned categories with article counts",
      description =
          "Includes enabled and disabled categories. Stable keys, editable names/colors, current article counts, and optimistic versions. Seeding preserves edited defaults.")
  public CatalogResponse list() {
    var account = current.requireId();
    catalog.ensureDefaults(account);
    return new CatalogResponse(catalog.list(account).stream().map(mapper::toResponse).toList());
  }

  @PostMapping(consumes = "application/json")
  @Operation(
      operationId = "createCategory",
      summary = "Create an owned custom category",
      description =
          "Unique account name ignoring case and surrounding whitespace; configurable name limit defaults to 100 Unicode code points. Color uses #RRGGBB.")
  @ApiResponse(
      responseCode = "201",
      description = "Created category, including stable key, version and articleCount.")
  public ResponseEntity<CategoryResponse> create(@RequestBody CreateCategoryRequest request) {
    var result = management.create(request.displayName(), request.color());
    return ResponseEntity.created(URI.create("/api/v1/categories/" + result.key()))
        .body(mapper.toResponse(result));
  }

  @PatchMapping(value = "/{key}", consumes = "application/json")
  @Operation(
      operationId = "editCategory",
      summary = "Edit, disable or re-enable an owned category",
      description =
          "Expected JSON version required. Omitted fields remain unchanged; null/unknown fields rejected. Names/colors editable for all categories. Cannot disable a category containing articles, or the OTHER fallback. No-op preserves version.")
  public CategoryResponse edit(@PathVariable String key, @RequestBody EditCategoryRequest request) {
    return mapper.toResponse(
        management.edit(
            key, request.version(), request.displayName(), request.color(), request.enabled()));
  }

  @DeleteMapping("/{key}")
  @Operation(
      operationId = "deleteCategory",
      summary = "Remove an empty custom category",
      description =
          "Version required in query. Referenced categories and predefined categories cannot be removed. Missing and foreign keys return the same 404.")
  @ApiResponse(responseCode = "204", description = "Category permanently removed.")
  public ResponseEntity<Void> delete(@PathVariable String key, @RequestParam Long version) {
    management.remove(key, version);
    return ResponseEntity.noContent().build();
  }

  @Mapper
  public interface CategoryApiMapper {
    CategoryResponse toResponse(com.know.categories.application.Category category);
  }
}
