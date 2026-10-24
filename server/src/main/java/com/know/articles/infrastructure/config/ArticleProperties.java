package com.know.articles.infrastructure.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("know.articles")
public record ArticleProperties(@Positive int maxContentBytes, @Positive int maxTitleCodePoints) {}
