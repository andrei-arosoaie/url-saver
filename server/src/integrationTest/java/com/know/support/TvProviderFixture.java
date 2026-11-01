package com.know.support;

import static com.know.entities.application.TvModels.*;

import com.know.entities.application.TvProvider;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TvProviderFixture implements TvProvider {
  public final AtomicInteger searches = new AtomicInteger();
  public volatile boolean unavailable;

  public List<Candidate> search(String query) {
    searches.incrementAndGet();
    if (unavailable)
      throw new com.know.shared.application.RequestFailure(
          503, "TVMAZE_UNAVAILABLE", "Unavailable");
    if (query.equals("empty")) return List.of();
    return java.util.stream.LongStream.rangeClosed(1, 12)
        .mapToObj(id -> new Candidate(id, "Same title", 2000 + (int) id, "Running", null))
        .toList();
  }

  public Show show(long id) {
    if (id <= 0)
      throw new com.know.shared.application.RequestFailure(400, "INVALID_REQUEST", "Invalid ID");
    return new Show(id, "Same title", null, "Synopsis", List.of("Drama"), "Running", 45, 8.0, null);
  }

  public Catalog catalog(long id) {
    long season = id * 100;
    return new Catalog(
        show(id),
        List.of(new Season(season, 1, null, 0, 0, 0)),
        List.of(
            episode(id, 1, Instant.parse("2026-01-01T00:00:00Z")),
            episode(id, 2, Instant.parse("2026-01-02T00:00:00Z")),
            episode(id, 3, null),
            episode(id, 4, Instant.parse("2026-10-26T00:00:00Z"))));
  }

  private Episode episode(long show, int number, Instant air) {
    return new Episode(
        show * 1000 + number,
        show,
        show * 100,
        1,
        number,
        "Episode " + number,
        air,
        null,
        45,
        null,
        "Episode synopsis",
        false,
        false);
  }
}
