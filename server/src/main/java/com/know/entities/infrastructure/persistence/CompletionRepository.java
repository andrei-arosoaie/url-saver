package com.know.entities.infrastructure.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface CompletionRepository extends JpaRepository<CompletionEntity, UUID> {
  Optional<CompletionEntity> findByIdAndAccountIdAndEntityId(UUID id, UUID owner, UUID entity);

  Optional<CompletionEntity> findByIdAndAccountId(UUID id, UUID owner);
}
