package com.know.screenshots.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CleanupRepository extends JpaRepository<CleanupEntity, UUID> {
  List<CleanupEntity> findTop20ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(Instant time);
}
