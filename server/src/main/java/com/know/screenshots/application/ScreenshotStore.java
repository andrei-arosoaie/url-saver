package com.know.screenshots.application;

import com.know.screenshots.application.ScreenshotStorage.StoredFile;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ScreenshotStore {
  State read(UUID accountId, UUID articleId);

  Claim claim(
      UUID accountId,
      UUID articleId,
      UUID captureId,
      long expectedVersion,
      String sourceHash,
      String provider,
      Instant now);

  void journal(UUID leaseId, StoredFile planned, Instant now);

  boolean complete(
      UUID accountId,
      UUID articleId,
      UUID captureId,
      UUID leaseId,
      StoredFile file,
      ScreenshotStorage.ImageFile image);

  void failed(UUID accountId, UUID articleId, UUID leaseId);

  List<Cleanup> due(Instant now);

  void cleaned(UUID id);

  void postpone(Cleanup cleanup, Instant now);

  record State(
      long version,
      UUID captureId,
      StoredFile file,
      String contentType,
      Long byteSize,
      Integer width,
      Integer height) {}

  record Claim(State state, UUID leaseId) {}

  record Cleanup(UUID id, StoredFile file, Instant retainUntil, int attempts) {}
}
