package com.know.articles.presentation;

import com.know.articles.application.SaveResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"outcome", "article"})
public record SaveArticleResponse(SaveResult.Outcome outcome, ArticleResponse article) {}
