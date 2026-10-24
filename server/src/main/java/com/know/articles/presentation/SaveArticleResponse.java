package com.know.articles.presentation;

import com.know.articles.application.SaveResult;

public record SaveArticleResponse(SaveResult.Outcome outcome, ArticleResponse article) {}
