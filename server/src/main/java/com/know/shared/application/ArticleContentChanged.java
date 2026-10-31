package com.know.shared.application;

import java.util.UUID;

public record ArticleContentChanged(UUID accountId, UUID articleId) {}
