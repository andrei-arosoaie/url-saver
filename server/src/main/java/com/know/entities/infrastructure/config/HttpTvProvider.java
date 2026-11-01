package com.know.entities.infrastructure.config;

import static com.know.entities.application.TvModels.*;

import com.know.entities.application.*;
import com.know.shared.application.RequestFailure;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class HttpTvProvider implements TvProvider {
  private final TvProperties config;
  private final TvStore store;
  private final JsonMapper json;
  private final Clock entityClock;
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public List<Candidate> search(String query) {
    var data = get("/search/shows?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
    if (!data.isArray()) throw unavailable();
    var items = new ArrayList<Candidate>();
    for (var entry : data) {
      var s = entry.path("show");
      var premiered = text(s, "premiered");
      items.add(
          new Candidate(
              id(s),
              required(s, "name"),
              premiered == null ? null : LocalDate.parse(premiered).getYear(),
              text(s, "status"),
              image(s)));
    }
    return items;
  }

  public Show show(long id) {
    if (id <= 0)
      throw new RequestFailure(400, "INVALID_REQUEST", "A positive show ID is required.");
    var show = show(get("/shows/" + id));
    if (show.tvmazeId() != id) throw unavailable();
    return show;
  }

  public Catalog catalog(long id) {
    var show = show(id);
    var seasonData = get("/shows/" + id + "/seasons");
    var episodeData = get("/shows/" + id + "/episodes");
    if (!seasonData.isArray() || !episodeData.isArray()) throw unavailable();
    var seasons = new ArrayList<Season>();
    var byNumber = new HashMap<Integer, Long>();
    for (var s : seasonData) {
      if (!s.path("number").isInt()) throw unavailable();
      var season = new Season(id(s), s.path("number").asInt(), text(s, "name"), 0, 0, 0);
      seasons.add(season);
      byNumber.put(season.number(), season.id());
    }
    var episodes = new ArrayList<Episode>();
    for (var e : episodeData) {
      if (!"regular".equals(text(e, "type"))) continue;
      var air = text(e, "airstamp");
      var date = text(e, "airdate");
      int season = e.path("season").asInt(-1);
      if (season < 0 || !byNumber.containsKey(season)) throw unavailable();
      episodes.add(
          new Episode(
              id(e),
              id,
              byNumber.get(season),
              season,
              integer(e, "number"),
              required(e, "name"),
              air == null ? null : OffsetDateTime.parse(air).toInstant(),
              date == null ? null : LocalDate.parse(date),
              integer(e, "runtime"),
              image(e),
              summary(e),
              false,
              false));
    }
    var next =
        episodes.stream()
            .filter(e -> e.airAt() != null && e.airAt().isAfter(entityClock.instant()))
            .min(Comparator.comparing(Episode::airAt))
            .orElse(null);
    return new Catalog(
        new Show(
            show.tvmazeId(),
            show.name(),
            show.posterUrl(),
            show.summary(),
            show.genres(),
            show.providerStatus(),
            show.averageRuntime(),
            show.tvmazeRating(),
            show.imdbUrl(),
            next,
            show.premiereYear()),
        seasons,
        episodes);
  }

  private Show show(JsonNode s) {
    var genres = new ArrayList<String>();
    for (var g : s.path("genres")) if (g.isString()) genres.add(g.asString());
    var imdb = text(s.path("externals"), "imdb");
    var rating = s.path("rating").path("average");
    return new Show(
        id(s),
        required(s, "name"),
        image(s),
        summary(s),
        genres,
        text(s, "status"),
        integer(s, "averageRuntime"),
        rating.isNumber() ? rating.asDouble() : null,
        imdb != null && imdb.matches("tt[0-9]+")
            ? "https://www.imdb.com/title/" + imdb + "/"
            : null,
        null,
        text(s, "premiered") == null ? null : LocalDate.parse(text(s, "premiered")).getYear());
  }

  private JsonNode get(String path) {
    var token = UUID.randomUUID();
    var now = entityClock.instant();
    if (!store.claimProvider(token, now, now.plus(config.leaseDuration()))) throw unavailable();
    Instant blocked = null;
    try {
      var request =
          HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
              .timeout(Duration.ofSeconds(15))
              .header("Accept", "application/json")
              .header("User-Agent", "Know/1.0 (TV catalog)")
              .GET()
              .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (var body = response.body()) {
        if (response.statusCode() != 200) {
          blocked =
              retryAt(
                  response.headers().firstValue("Retry-After").orElse(null), entityClock.instant());
          if (response.statusCode() == 404)
            throw new RequestFailure(
                404, "PROVIDER_SHOW_NOT_FOUND", "The selected show is unavailable.");
          throw unavailable();
        }
        byte[] bytes = body.readNBytes(4_000_001);
        if (bytes.length > 4_000_000) throw unavailable();
        return json.readTree(bytes);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      blocked = now.plus(config.retryDelay());
      throw unavailable();
    } catch (java.io.IOException | IllegalArgumentException e) {
      blocked = now.plus(config.retryDelay());
      throw unavailable();
    } finally {
      store.releaseProvider(token, blocked);
    }
  }

  private Instant retryAt(String value, Instant now) {
    try {
      return now.plusSeconds(Math.clamp(Long.parseLong(value), 1, 3600));
    } catch (RuntimeException ignored) {
      try {
        var date = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        return date.isAfter(now) ? date : now.plus(config.retryDelay());
      } catch (RuntimeException invalid) {
        return now.plus(config.retryDelay());
      }
    }
  }

  private static long id(JsonNode n) {
    long id = n.path("id").asLong(-1);
    if (id <= 0) throw unavailable();
    return id;
  }

  private static String required(JsonNode n, String key) {
    var value = text(n, key);
    if (value == null) throw unavailable();
    return value;
  }

  private static String text(JsonNode n, String key) {
    var v = n.path(key);
    return v.isString() && !v.asString().isBlank() ? v.asString() : null;
  }

  private static Integer integer(JsonNode n, String key) {
    var v = n.path(key);
    return v.isInt() ? v.asInt() : null;
  }

  private static String image(JsonNode n) {
    var url = text(n.path("image"), "medium");
    return url != null && url.startsWith("https://") ? url : null;
  }

  private static String summary(JsonNode n) {
    var value = text(n, "summary");
    return value == null ? null : Jsoup.parse(value).text();
  }

  private static RequestFailure unavailable() {
    return new RequestFailure(
        503,
        "TVMAZE_UNAVAILABLE",
        "Show information is temporarily unavailable. Try again shortly.");
  }
}
