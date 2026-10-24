package com.know.articles.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"items", "nextCursor"})
public record ArticlePageResponse(
    List<ArticleSummaryResponse> items,
    @Schema(
            types = {"string", "null"},
            description = "Opaque bookmark for Load more; null at the end.")
        String nextCursor) {}
