package com.know.articles.presentation;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    requiredProperties = {"url", "title"},
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record SaveArticleRequest(
    @Schema(description = "Exact absolute HTTP/HTTPS URL; surrounding whitespace is rejected.")
        String url,
    @Schema(
            description =
                "Nonblank title; configurable maximum defaults to 1000 Unicode code points.")
        String title,
    @Schema(
            types = {"string", "null"},
            description =
                "Optional article HTML. Supply no content for a link-only save; provided content must contain article text.")
        String content) {}
