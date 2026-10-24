package com.know.articles.presentation;

import java.time.Instant;
import java.util.UUID;

public record ArticleResponse(
    UUID id, String url, String title, String content, Instant savedAt, long version) {}
