package com.know.articles.application;

import java.time.Instant;
import java.util.UUID;

public record ArticleSummary(
    UUID id, String url, String title, Instant savedAt, long version, boolean hasContent) {}
