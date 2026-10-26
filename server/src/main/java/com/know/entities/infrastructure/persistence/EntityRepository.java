package com.know.entities.infrastructure.persistence;

import com.know.entities.application.EntityType;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface EntityRepository extends JpaRepository<TrackedEntity, UUID> {
  Optional<TrackedEntity> findByIdAndAccountId(UUID id, UUID owner);

  Optional<TrackedEntity> findByAccountIdAndTypeAndTitleHash(
      UUID owner, EntityType type, byte[] hash);
}
