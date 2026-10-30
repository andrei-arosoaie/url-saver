package com.know.shared.application;

import java.util.UUID;

/** Synchronous transaction event: adapters detach dependent resources before article removal. */
public record ArticleDeleting(UUID accountId, UUID articleId) {}
