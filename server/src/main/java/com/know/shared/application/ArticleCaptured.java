package com.know.shared.application;

import java.util.UUID;

/** Published within the first-save transaction, so durable work commits with the article. */
public record ArticleCaptured(UUID accountId, UUID articleId) {}
