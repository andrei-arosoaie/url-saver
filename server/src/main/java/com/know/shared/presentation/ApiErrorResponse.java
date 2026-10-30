package com.know.shared.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(
    description =
        "Spring Boot default error JSON with optional application reason and duplicate resource ID.",
    requiredProperties = {"timestamp", "status", "error", "path"})
public record ApiErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String path,
    String message,
    @Schema(
            description =
                "INVALID_REQUEST, INVALID_ENTITY_STATUS, UNAUTHENTICATED, ARTICLE_NOT_FOUND, RESOURCE_NOT_FOUND, DUPLICATE_URL, DUPLICATE_ENTITY, VERSION_CONFLICT, CONTENT_TOO_LARGE, DUPLICATE_CATEGORY_NAME, CATEGORY_IN_USE, CATEGORY_REQUIRED, DEFAULT_CATEGORY_PROTECTED, URL_HASH_COLLISION or ENTITY_TITLE_HASH_COLLISION.")
        String reason,
    @Schema(description = "Present only for DUPLICATE_URL.") UUID existingArticleId,
    @Schema(description = "Present only for DUPLICATE_ENTITY.") UUID existingEntityId) {}
