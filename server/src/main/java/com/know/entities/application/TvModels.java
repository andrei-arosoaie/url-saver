package com.know.entities.application;

import java.time.*;
import java.util.*;

public final class TvModels {
  private TvModels() {}

  public record Candidate(
      long tvmazeId, String name, Integer premiereYear, String providerStatus, String posterUrl) {}

  public record Search(
      List<Candidate> items,
      String matchStatus,
      String state,
      int minimumCharacters,
      int debounceMs,
      int pageSize) {}

  public record Show(
      long tvmazeId,
      String name,
      String posterUrl,
      String summary,
      List<String> genres,
      String providerStatus,
      Integer averageRuntime,
      Double tvmazeRating,
      String imdbUrl,
      Episode nextEpisode,
      Integer premiereYear) {
    public Show(
        long tvmazeId,
        String name,
        String posterUrl,
        String summary,
        List<String> genres,
        String providerStatus,
        Integer averageRuntime,
        Double tvmazeRating,
        String imdbUrl,
        Episode nextEpisode) {
      this(
          tvmazeId,
          name,
          posterUrl,
          summary,
          genres,
          providerStatus,
          averageRuntime,
          tvmazeRating,
          imdbUrl,
          nextEpisode,
          null);
    }

    public Show(
        long tvmazeId,
        String name,
        String posterUrl,
        String summary,
        List<String> genres,
        String providerStatus,
        Integer averageRuntime,
        Double tvmazeRating,
        String imdbUrl) {
      this(
          tvmazeId,
          name,
          posterUrl,
          summary,
          genres,
          providerStatus,
          averageRuntime,
          tvmazeRating,
          imdbUrl,
          null,
          null);
    }
  }

  public record Season(
      long id,
      int number,
      String name,
      long regularEpisodeCount,
      long airedCount,
      long watchedCount) {}

  public record Episode(
      long id,
      long showId,
      Long seasonId,
      int season,
      Integer number,
      String title,
      Instant airAt,
      LocalDate airDate,
      Integer runtime,
      String imageUrl,
      String summary,
      boolean watched,
      boolean canMarkWatched) {}

  public record Catalog(Show show, List<Season> seasons, List<Episode> episodes) {}

  public record Mapping(
      UUID entityId,
      long tvmazeId,
      String alias,
      Long lastWatchedEpisodeId,
      Instant lastMarkedSeenAt,
      boolean newEpisodesAvailable,
      long version) {}

  public record Metadata(String state, Instant fetchedAt, Instant nextAttemptAt) {}

  public record Details(
      UUID entityId,
      long tvmazeId,
      long version,
      long entityVersion,
      String canonicalTitle,
      String alias,
      String status,
      Show show,
      Long lastWatchedEpisodeId,
      long watchedCount,
      long airedCount,
      boolean newEpisodesAvailable,
      boolean canMarkSeen,
      Metadata metadata) {}

  public record Seasons(List<Season> items, String metadataStatus) {}

  public record Episodes(List<Episode> items, String metadataStatus) {}

  public record Lease(long showId, UUID token) {}

  public record CachedSearch(List<Candidate> items, Instant fetchedAt, Instant expiresAt) {}
}
