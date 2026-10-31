package com.know.identification.application;

import java.time.Duration;
import java.util.List;

public record IdentificationPolicy(
    boolean enabled,
    List<String> providerOrder,
    int consecutiveFailureThreshold,
    Duration providerCooldown,
    Duration jobLease,
    int chunkCharacters,
    int maxChunks,
    int maxNameCodePoints) {}
