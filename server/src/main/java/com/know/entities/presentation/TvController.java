package com.know.entities.presentation;

import com.know.entities.application.*;
import com.know.entities.application.TvModels.*;
import com.know.shared.application.RequestFailure;
import io.swagger.v3.oas.annotations.Operation;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@com.know.shared.presentation.EntityApiErrors
@RequestMapping(value = "/api/v1", produces = "application/json")
public class TvController {
  private final TvService service;
  private final TvSearch search;
  private final TvProvider provider;
  private final EntityApiMapper mapper;
  private final com.know.accounts.application.CurrentAccount current;
  private final TvPolicy policy;

  public record AutocompleteSettings(int minimumCharacters, int debounceMs, int pageSize) {}

  @GetMapping("/tvmaze/settings")
  public AutocompleteSettings settings() {
    current.requireId();
    return new AutocompleteSettings(
        policy.minimumCharacters(), policy.debounceMs(), policy.pageSize());
  }

  public record LinkRequest(Long entityVersion, long tvmazeId) {}

  public record CreateRequest(long tvmazeId, String status) {}

  public record AliasRequest(
      Long version, @com.fasterxml.jackson.annotation.JsonProperty(required = true) String alias) {}

  public record PositionRequest(Long version, long episodeId, Boolean markPreviousWatched) {}

  public record WatchRequest(Long version, Boolean watched) {}

  public record SeenRequest(Long version, Long entityVersion) {}

  public record ChangeRequest(
      Long version, Long entityVersion, long tvmazeId, boolean confirmResetProgress) {}

  @GetMapping("/tvmaze/shows")
  @Operation(operationId = "searchTvShows", summary = "Search shared TV show catalog")
  public Search search(@RequestParam String query) {
    return search.search(query);
  }

  @PostMapping("/tvmaze/entities")
  @Operation(
      operationId = "createIdentifiedTvShow",
      summary = "Atomically create a TV show with selected provider identity")
  public ResponseEntity<EntityResponses.EntityResponse> create(@RequestBody CreateRequest r) {
    current.requireId();
    var result = service.create(provider.show(r.tvmazeId()), r.status());
    return ResponseEntity.created(URI.create("/api/v1/entities/" + result.id()))
        .body(mapper.toResponse(result));
  }

  @PutMapping("/entities/{id}/tv-show")
  public Details link(@PathVariable UUID id, @RequestBody LinkRequest r) {
    serviceCheck(id);
    return service.link(id, r.entityVersion(), provider.show(r.tvmazeId()));
  }

  @GetMapping("/entities/{id}/tv-show")
  public Details details(@PathVariable UUID id) {
    return service.read(id);
  }

  @GetMapping("/entities/{id}/tv-show/seasons")
  public Seasons seasons(@PathVariable UUID id) {
    return service.seasons(id);
  }

  @GetMapping("/entities/{id}/tv-show/seasons/{seasonId}/episodes")
  public Episodes episodes(@PathVariable UUID id, @PathVariable long seasonId) {
    return service.episodes(id, seasonId);
  }

  @PatchMapping("/entities/{id}/tv-show")
  public Details alias(@PathVariable UUID id, @RequestBody AliasRequest r) {
    return service.alias(id, r.version(), r.alias());
  }

  @PatchMapping("/entities/{id}/tv-show/position")
  public Details position(@PathVariable UUID id, @RequestBody PositionRequest r) {
    return service.position(
        id, r.version(), r.episodeId(), Boolean.TRUE.equals(r.markPreviousWatched()));
  }

  @PutMapping("/entities/{id}/tv-show/episodes/{episodeId}/watched")
  public Details watch(
      @PathVariable UUID id, @PathVariable long episodeId, @RequestBody WatchRequest r) {
    if (r.watched() == null)
      throw new RequestFailure(400, "INVALID_REQUEST", "watched is required.");
    return service.watch(id, r.version(), episodeId, r.watched());
  }

  @PostMapping("/entities/{id}/tv-show/mark-seen")
  public Details seen(@PathVariable UUID id, @RequestBody SeenRequest r) {
    return service.markSeen(id, r.version(), r.entityVersion());
  }

  @PostMapping("/entities/{id}/tv-show/change-show")
  public Details change(@PathVariable UUID id, @RequestBody ChangeRequest r) {
    serviceCheck(id);
    if (!r.confirmResetProgress())
      throw new RequestFailure(
          400, "CONFIRMATION_REQUIRED", "Confirm progress reset before replacing a show.");
    return service.replace(id, r.version(), r.entityVersion(), provider.show(r.tvmazeId()), true);
  }

  private void serviceCheck(UUID id) {
    // Check ownership before any provider request, including invalid/stale remapping attempts.
    current.requireId();
    var entity = entityService.detail(id);
    if (entity.type() != EntityType.TV_SHOW)
      throw new RequestFailure(400, "INVALID_ENTITY_TYPE", "Select a TV show.");
  }

  private final EntityService entityService;
}
