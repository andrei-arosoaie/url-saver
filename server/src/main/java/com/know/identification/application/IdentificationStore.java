package com.know.identification.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface IdentificationStore {
  void enqueue(UUID account, UUID article, Instant now, boolean explicit);

  Identification.Status status(UUID account, UUID article);

  Identification.Job claim(Instant now, Duration lease);

  void finish(
      Identification.Job job,
      String status,
      String reason,
      String provider,
      String model,
      List<Identification.Subject> subjects,
      Instant now);

  Identification.ProviderPermit acquire(String provider, Instant now, Duration lease);

  void success(Identification.ProviderPermit permit);

  void failure(
      Identification.ProviderPermit permit,
      boolean quota,
      IdentificationPolicy policy,
      Instant now);
}
