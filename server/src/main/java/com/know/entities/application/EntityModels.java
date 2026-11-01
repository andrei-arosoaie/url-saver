package com.know.entities.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EntityModels {
  private EntityModels() {}

  public record Details(
      UUID id,
      EntityType type,
      String title,
      String status,
      Instant createdAt,
      long version,
      Instant lastCompletedAt,
      long totalCompletionCount,
      TvSummary tvShow) {
    public Details(
        UUID id,
        EntityType type,
        String title,
        String status,
        Instant createdAt,
        long version,
        Instant lastCompletedAt,
        long totalCompletionCount) {
      this(
          id, type, title, status, createdAt, version, lastCompletedAt, totalCompletionCount, null);
    }
  }

  public record Summary(
      UUID id,
      EntityType type,
      String title,
      String status,
      Instant createdAt,
      long version,
      Instant lastCompletedAt,
      long totalCompletionCount,
      long completionCount,
      Instant latestMatchingCompletionAt,
      TvSummary tvShow) {}

  public record TvSummary(
      long tvmazeId, String alias, String position, boolean newEpisodesAvailable) {}

  public record Completion(
      UUID id, UUID entityId, Instant completedAt, Instant createdAt, long version) {}

  public record LinkedEntity(
      UUID id,
      EntityType type,
      String title,
      String status,
      Instant createdAt,
      long version,
      Instant lastCompletedAt,
      long totalCompletionCount,
      Instant linkedAt) {}

  public record Link(UUID articleId, UUID entityId, Instant linkedAt) {}

  public record LinkResult(String outcome, Details entity, Link link) {}

  public record CompletionResult(Details entity, Completion completion) {}

  public record Year(int year, long entityCount, long completionCount) {}

  public record Page<T>(List<T> items, String nextCursor) {
    public Page {
      items = List.copyOf(items);
    }
  }

  public record Filter(EntityType type, String query, String status, Integer completionYear) {}

  public record Create(EntityType type, String title, String status, Instant completedAt) {}

  public record Edit(Long version, String title, String status, Instant completedAt) {}
}
