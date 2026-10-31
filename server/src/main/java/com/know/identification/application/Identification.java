package com.know.identification.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class Identification {
  private Identification() {}

  public record Subject(String name, boolean main) {}

  public record Result(boolean sufficient, String category, List<Subject> subjects) {}

  public record Choice(String key, String name) {}

  public record Request(
      String text, List<Choice> categories, boolean categoryNeeded, boolean nameNeeded) {}

  public record Status(
      String status,
      String reason,
      String provider,
      String model,
      List<Subject> subjects,
      String categorySource,
      String nameSource,
      Instant requestedAt,
      Instant completedAt) {}

  public record Job(UUID accountId, UUID articleId, UUID claimId) {}

  public record ProviderPermit(String provider, UUID claimId) {}
}
