package com.know.shared.presentation;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.*;
import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponses({
  @ApiResponse(
      responseCode = "400",
      description = "INVALID_REQUEST or INVALID_ENTITY_STATUS; no partial changes.",
      content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
  @ApiResponse(
      responseCode = "401",
      description = "UNAUTHENTICATED when temporary account mode is disabled.",
      content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
  @ApiResponse(
      responseCode = "404",
      description =
          "RESOURCE_NOT_FOUND for a missing or foreign-owned parent/record; no ownership disclosure.",
      content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
  @ApiResponse(
      responseCode = "409",
      description =
          "VERSION_CONFLICT requires reload; DUPLICATE_ENTITY includes existingEntityId. No automatic overwrite.",
      content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
  @ApiResponse(
      responseCode = "500",
      description =
          "Unexpected failure; ENTITY_TITLE_HASH_COLLISION never reuses a different full title.",
      content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public @interface EntityApiErrors {}
