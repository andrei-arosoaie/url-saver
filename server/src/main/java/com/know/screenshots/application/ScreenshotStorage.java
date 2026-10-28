package com.know.screenshots.application;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Provider boundary: no SDK types, secrets or permanent public delivery URLs. */
public interface ScreenshotStorage {
  String provider();

  StoredFile planned(UUID accountId, UUID uploadId, String contentType);

  StoredFile upload(UUID accountId, UUID captureId, ImageFile image);

  Download sign(StoredFile file, Duration lifetime);

  void delete(StoredFile file);

  record ImageFile(byte[] bytes, String contentType, int width, int height) {}

  record StoredFile(String provider, String id, String path) {}

  record Download(String url, Instant expiresAt) {}
}
