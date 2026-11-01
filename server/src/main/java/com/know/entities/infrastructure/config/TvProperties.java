package com.know.entities.infrastructure.config;

import com.know.entities.application.TvPolicy;
import jakarta.validation.constraints.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("know.tvmaze")
public record TvProperties(
    @DefaultValue("https://api.tvmaze.com") String baseUrl,
    @DefaultValue("2") @Min(2) int minimumCharacters,
    @DefaultValue("300") @Positive int debounceMs,
    @DefaultValue("8") @Positive int pageSize,
    @DefaultValue("10000") @Positive int cacheRowLimit,
    @DefaultValue("1h") Duration catalogTtl,
    @DefaultValue("24h") Duration positiveTtl,
    @DefaultValue("15m") Duration emptyTtl,
    @DefaultValue("7d") Duration staleWindow,
    @DefaultValue("2m") Duration leaseDuration,
    @DefaultValue("30s") Duration retryDelay)
    implements TvPolicy {}
