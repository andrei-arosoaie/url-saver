package com.know.entities.application;

import static com.know.entities.application.EntityModels.*;

import com.know.shared.application.ScopedCursor.Position;
import java.time.Instant;
import java.util.*;

public interface EntityStore {
  boolean identified(UUID owner, UUID id);

  Details createIdentified(UUID owner, TvModels.Show show, String status, Instant now);

  void lockWrites(UUID owner);

  Optional<Details> find(UUID owner, UUID id);

  Optional<Details> findExact(UUID owner, EntityType type, String title);

  Details insert(UUID owner, Create command, Instant now);

  Details revise(UUID owner, UUID id, String title, String status, boolean historyChanged);

  List<Summary> list(UUID owner, Filter filter, Position before, int limit);

  List<Year> years(UUID owner, Filter filter);

  Completion addCompletion(UUID owner, UUID entityId, Instant completedAt, Instant now);

  Optional<Completion> completion(UUID owner, UUID entityId, UUID id);

  Completion correctCompletion(UUID owner, UUID id, Instant time);

  void deleteCompletion(UUID owner, UUID id);

  List<Completion> completions(UUID owner, UUID entityId, Position before, int limit);

  Optional<Link> link(UUID owner, UUID articleId, UUID entityId);

  Link addLink(UUID owner, UUID articleId, UUID entityId, Instant now);

  void unlink(UUID owner, UUID articleId, UUID entityId);

  List<LinkedEntity> linkedEntities(UUID owner, UUID articleId, Position before, int limit);
}
