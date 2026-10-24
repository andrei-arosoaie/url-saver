package com.know.articles.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(requiredProperties = {"id", "url", "title", "savedAt", "version", "hasContent"})
public record ArticleSummaryResponse(
    UUID id,
    String url,
    String title,
    Instant savedAt,
    @Schema(minimum = "0") long version,
    boolean hasContent) {}
