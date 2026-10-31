package com.know.connections.application;

import static com.know.connections.application.ConnectionModels.*;

import com.know.shared.application.ScopedCursor;
import java.time.*;
import java.util.*;

public interface ConnectionStore {
  Profile profile(ConnectionPolicy policy, Instant now);

  Article article(UUID owner, UUID id);

  void changed(UUID owner, UUID id, Profile profile, Instant now, boolean incrementRevision);

  void retry(UUID owner, UUID id, Profile profile, Instant now);

  Job claim(Instant now, Duration lease);

  int resumeConfiguredWork(UUID profile, Instant now);

  boolean renew(Job job, UUID permit, Instant now, Duration lease);

  boolean current(Job job, Instant now);

  void defer(Job job, Instant until, String code, String message, Instant now);

  void failed(Job job, EmbeddingFailure failure, ConnectionPolicy policy, Instant now);

  void finish(Job job, byte[] hash, Instant now);

  Permit acquire(Job job, boolean embedding, ConnectionPolicy policy, Instant now);

  void release(Permit permit);

  void rateLimited(String scope, Instant until, Instant now);

  List<Chunk> plan(Job job, byte[] hash);

  void plan(Job job, byte[] hash, List<Chunk> plan, Instant now);

  boolean cached(Job job, byte[] hash, int chunkIndex, Chunk chunk);

  void vector(
      Job job, Article article, byte[] hash, int index, Chunk chunk, float[] vector, Instant now);

  void connect(Job job, ConnectionPolicy policy, Instant now);

  List<Related> related(UUID owner, UUID article, ScopedCursor.Position before, int limit);

  Progress progress(
      UUID owner, UUID article, Profile profile, ConnectionPolicy policy, Instant now);

  void remove(UUID owner, UUID article, UUID related, Instant now);

  void delete(UUID owner, UUID article);

  int backfill(Profile profile, int size, Instant now);
}
