package com.know.articles.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(requiredProperties = {"id", "url", "title", "content", "savedAt", "version"})
public record ArticleResponse(
    UUID id,
    String url,
    String title,
    @Schema(
            types = {"string", "null"},
            description = "Sanitized article HTML; null means link-only.")
        String content,
    Instant savedAt,
    @Schema(minimum = "0") long version) {}
