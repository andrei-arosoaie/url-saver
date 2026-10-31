package com.know.identification.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("know.identification")
public record IdentificationProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("true") boolean schedulingEnabled,
    @DefaultValue({"openrouter", "groq"}) List<String> providerOrder,
    @DefaultValue("3") @Min(1) int consecutiveFailureThreshold,
    @DefaultValue("1d") Duration providerCooldown,
    @DefaultValue("10m") Duration jobLease,
    @DefaultValue("30s") Duration requestTimeout,
    @DefaultValue("6000") @Min(1000) @Max(12000) int chunkCharacters,
    @DefaultValue("8") @Min(1) @Max(32) int maxChunks,
    @Valid Map<String, Provider> providers) {
  public record Provider(@NotBlank String apiKey, @NotBlank String model, String baseUrl) {}

  public IdentificationProperties {
    if (providerCooldown == null
        || providerCooldown.isNegative()
        || providerCooldown.isZero()
        || jobLease == null
        || jobLease.isNegative()
        || jobLease.isZero()
        || requestTimeout == null
        || requestTimeout.isNegative()
        || requestTimeout.isZero())
      throw new IllegalArgumentException("Identification durations must be positive.");
    if (enabled
        && (providers == null
            || providerOrder == null
            || providerOrder.isEmpty()
            || providerOrder.stream().distinct().count() != providerOrder.size()
            || providerOrder.stream().anyMatch(p -> !providers.containsKey(p))))
      throw new IllegalArgumentException("Configure each enabled identification provider.");
    if (enabled
        && jobLease.compareTo(
                requestTimeout
                    .multipliedBy((long) maxChunks * providerOrder.size())
                    .plusSeconds(30))
            <= 0)
      throw new IllegalArgumentException("Job lease must exceed maximum provider processing time.");
  }
}
