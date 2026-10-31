package com.know.connections.infrastructure.config;

import com.know.connections.application.ConnectionPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("know.connections")
public record ConnectionProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("true") boolean schedulingEnabled,
    @DefaultValue("2") @Min(1) @Max(16) int localWorkers,
    @DefaultValue("8") @Min(1) @Max(100) int workerBatchSize,
    @DefaultValue("2s") Duration pollInterval,
    @DefaultValue("10s") Duration uiPollInterval,
    @DefaultValue("2m") Duration leaseDuration,
    @DefaultValue("10s") Duration heartbeatInterval,
    @DefaultValue("24h") Duration providerCooldown,
    @DefaultValue("1m") Duration backfillInterval,
    @DefaultValue("50") @Min(1) @Max(500) int backfillBatchSize,
    @DefaultValue({"1m", "2m", "4m", "8m", "16m"}) List<Duration> retryDelays,
    @DefaultValue("0.80") @DecimalMin("0") @DecimalMax("1") double threshold,
    @DefaultValue @Valid Embedding embedding) {
  public record Embedding(
      @DefaultValue("google") String provider,
      @DefaultValue("gemini-embedding-001") String model,
      @DefaultValue("768") @Min(128) @Max(3072) int dimensions,
      @DefaultValue("6000") @Min(256) @Max(12000) int chunkCharacters,
      @DefaultValue("256") @Min(0) int overlapCharacters,
      @DefaultValue("1024") @Min(32) @Max(2040) int maxTokens,
      @DefaultValue("8") @Min(1) @Max(100) int chunksPerPass,
      @DefaultValue("false") boolean liveRequestsEnabled,
      @DefaultValue("false") boolean unbilledProjectConfirmed,
      @DefaultValue("gemini-default") @NotBlank String quotaScope,
      @DefaultValue("1") @Min(1) @Max(16) int globalConcurrency,
      @DefaultValue("5") @Min(1) int requestsPerMinute,
      @DefaultValue("100") @Min(1) int embeddingsPerDay,
      @DefaultValue("30s") Duration requestTimeout,
      @DefaultValue @Valid Gemini gemini) {
    public Embedding {
      if (overlapCharacters >= chunkCharacters || overlapCharacters < 0)
        throw new IllegalArgumentException("Overlap must be smaller than a chunk.");
      if (!"google".equals(provider) || !"gemini-embedding-001".equals(model))
        throw new IllegalArgumentException(
            "Only the approved Gemini embedding model is configured.");
      if (liveRequestsEnabled
          && (!unbilledProjectConfirmed || gemini == null || gemini.apiKey().isBlank()))
        throw new IllegalArgumentException(
            "Confirm an unbilled project and configure the Gemini key before enabling requests.");
    }
  }

  public record Gemini(
      @DefaultValue("") String apiKey,
      @DefaultValue("https://generativelanguage.googleapis.com") String baseUrl) {}

  public ConnectionProperties {
    for (var duration :
        List.of(
            pollInterval,
            uiPollInterval,
            leaseDuration,
            heartbeatInterval,
            providerCooldown,
            backfillInterval,
            embedding.requestTimeout()))
      if (duration.isNegative() || duration.isZero())
        throw new IllegalArgumentException("Connection durations must be positive.");
    if (leaseDuration.compareTo(embedding.requestTimeout().plus(heartbeatInterval.multipliedBy(2)))
        <= 0)
      throw new IllegalArgumentException("Lease must exceed request timeout and renewal margin.");
    if (retryDelays.isEmpty() || retryDelays.stream().anyMatch(d -> d.isNegative() || d.isZero()))
      throw new IllegalArgumentException("Configure positive retry delays.");
  }

  public ConnectionPolicy policy() {
    var e = embedding;
    return new ConnectionPolicy(
        enabled,
        e.liveRequestsEnabled(),
        e.provider(),
        e.model(),
        e.dimensions(),
        e.chunkCharacters(),
        e.overlapCharacters(),
        e.maxTokens(),
        e.chunksPerPass(),
        threshold,
        e.quotaScope(),
        e.globalConcurrency(),
        e.requestsPerMinute(),
        e.embeddingsPerDay(),
        providerCooldown,
        e.requestTimeout(),
        leaseDuration,
        heartbeatInterval,
        pollInterval,
        uiPollInterval,
        retryDelays,
        backfillBatchSize);
  }
}
