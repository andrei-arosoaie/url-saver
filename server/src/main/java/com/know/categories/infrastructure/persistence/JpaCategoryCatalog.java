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
    var counts = new HashMap<String, Long>();
    repository.counts(account).forEach(c -> counts.put(c.getKey(), c.getCount()));
    return repository.findByAccountIdOrderByCreatedAtAscKeyAsc(account).stream()
        .map(c -> mapper.toRecord(c, counts.getOrDefault(c.getKey(), 0L)))
        .toList();
  }

  @Transactional
  public void lockEnabled(UUID account, String key) {
    if (key == null || repository.lock(account, key).filter(CategoryEntity::isEnabled).isEmpty())
      throw new RequestFailure(400, "INVALID_REQUEST", "Unknown or disabled category.");
  }

  @Transactional
  public Category lock(UUID account, String key) {
    return mapper.toRecord(owned(account, key), repository.countArticles(account, key));
  }

  @Transactional
  public Category create(UUID account, String name, String color) {
    try {
      return mapper.toRecord(
          repository.saveAndFlush(CategoryEntity.custom(account, name, color)), 0);
    } catch (org.springframework.dao.DataIntegrityViolationException failure) {
      throw duplicate();
    }
  }

  @Transactional
  public Category edit(
      UUID account, String key, long version, String name, String color, Boolean enabled) {
    var row = owned(account, key);
    checkVersion(row, version);
    var count = repository.countArticles(account, key);
    row.edit(name, color, enabled);
    try {
      repository.flush();
    } catch (org.springframework.dao.DataIntegrityViolationException failure) {
      throw duplicate();
    }
    return mapper.toRecord(row, count);
  }

  @Transactional
  public void remove(UUID account, String key, long version) {
    var row = owned(account, key);
    checkVersion(row, version);
    repository.delete(row);
    repository.flush();
  }

  private CategoryEntity owned(UUID account, String key) {
    return repository
        .lock(account, key)
        .orElseThrow(() -> new RequestFailure(404, "RESOURCE_NOT_FOUND", "Category not found."));
  }

  private void checkVersion(CategoryEntity row, long version) {
    if (row.getVersion() != version)
      throw new RequestFailure(
          409, "VERSION_CONFLICT", "Category changed; reload before updating.");
  }

  private RequestFailure duplicate() {
    return new RequestFailure(
        409, "DUPLICATE_CATEGORY_NAME", "A category with this name already exists.");
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
