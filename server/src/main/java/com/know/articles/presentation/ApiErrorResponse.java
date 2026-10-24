package com.know.articles.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(
    description =
        "Spring Boot default error JSON with optional application reason and duplicate article ID.",
    requiredProperties = {"timestamp", "status", "error", "path"})
public record ApiErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String path,
    String message,
    @Schema(
            description =
                "INVALID_REQUEST, UNAUTHENTICATED, ARTICLE_NOT_FOUND, DUPLICATE_URL, VERSION_CONFLICT, CONTENT_TOO_LARGE or URL_HASH_COLLISION.")
        String reason,
    @Schema(description = "Present only for DUPLICATE_URL.") UUID existingArticleId) {}
