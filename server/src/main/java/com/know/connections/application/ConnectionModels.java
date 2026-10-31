package com.know.connections.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ConnectionModels {
  private ConnectionModels() {}

  public enum Kind {
    EMBEDDING,
    CONNECTIONS
  }

  public record Profile(
      UUID id, String provider, String model, int dimensions, String configHash) {}

  public record Article(UUID accountId, UUID id, String content, long version, long revision) {}

  /** Offsets are UTF-16 indices on the extracted text, always on code-point boundaries. */
  public record Chunk(int start, int end, int tokens) {}

  public record Job(
      UUID id,
      UUID accountId,
      UUID articleId,
      Kind kind,
      UUID profileId,
      long revision,
      UUID leaseToken,
      int attempts,
      byte[] contentHash,
      List<Chunk> plan) {}

  public record Permit(UUID token, String scope, Instant waitUntil, String reason) {
    public boolean acquired() {
      return token != null;
    }
  }

  public record SharedEntity(UUID id, String name) {}

  public record Related(
      UUID id,
      UUID connectionId,
      String title,
      String entityName,
      String category,
      String url,
      Instant savedAt,
      List<SharedEntity> sharedEntities,
      Double semanticScore,
      boolean semanticUpdating) {}

  public record Progress(String state, String reason, Instant nextAttemptAt, long pollIntervalMs) {}

  public record Page(List<Related> items, String nextCursor, Progress processing) {}
}
