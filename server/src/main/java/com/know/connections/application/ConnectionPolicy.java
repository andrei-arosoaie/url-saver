package com.know.connections.application;

import java.time.Duration;
import java.util.List;

public record ConnectionPolicy(
    boolean enabled,
    boolean liveRequestsEnabled,
    String provider,
    String model,
    int dimensions,
    int chunkCharacters,
    int overlapCharacters,
    int maxTokens,
    int chunksPerPass,
    double threshold,
    String quotaScope,
    int globalConcurrency,
    int requestsPerMinute,
    int embeddingsPerDay,
    Duration providerCooldown,
    Duration requestTimeout,
    Duration leaseDuration,
    Duration heartbeatInterval,
    Duration pollInterval,
    Duration uiPollInterval,
    List<Duration> retryDelays,
    int backfillBatchSize) {}
