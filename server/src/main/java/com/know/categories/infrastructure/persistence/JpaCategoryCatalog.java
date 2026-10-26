package com.know.categories.infrastructure.persistence;

import com.know.categories.application.*;
import com.know.shared.application.RequestFailure;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@RequiredArgsConstructor
public class JpaCategoryCatalog implements CategoryCatalog {
  private final CategoryRepository repository;
  private final CategoryMapper mapper;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void ensureDefaults(UUID account) {
    repository.seed(account);
  }

  @Transactional(readOnly = true)
  public List<Category> list(UUID account) {
    return repository.findByAccountIdOrderByCreatedAtAscKeyAsc(account).stream()
        .map(mapper::toRecord)
        .toList();
  }

  @Transactional(readOnly = true)
  public void requireEnabled(UUID account, String key) {
    if (key == null
        || repository
            .findByAccountIdAndKey(account, key)
            .filter(CategoryEntity::isEnabled)
            .isEmpty())
      throw new RequestFailure(400, "INVALID_REQUEST", "Unknown or disabled category.");
  }
}
