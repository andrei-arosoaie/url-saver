package com.know.categories.application;

import com.know.accounts.application.CurrentAccount;
import com.know.shared.application.RequestFailure;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryManagement {
  private final CurrentAccount account;
  private final CategoryCatalog catalog;
  private final CategoryRules rules;

  @Transactional
  public Category create(String name, String color) {
    var owner = account.requireId();
    var clean = rules.name(name);
    rules.color(color);
    catalog.ensureDefaults(owner);
    return catalog.create(owner, clean, color);
  }

  @Transactional
  public Category edit(String key, Long version, String name, String color, Boolean enabled) {
    var owner = account.requireId();
    version(version);
    if (name == null && color == null && enabled == null) throw invalid();
    var clean = name == null ? null : rules.name(name);
    if (color != null) rules.color(color);
    var category = catalog.lock(owner, key);
    checkVersion(category, version);
    if (Boolean.FALSE.equals(enabled)) {
      requireEmpty(category);
      if (key.equals("OTHER"))
        throw new RequestFailure(
            409, "CATEGORY_REQUIRED", "Other must remain enabled for uncategorized saves.");
    }
    return catalog.edit(owner, key, version, clean, color, enabled);
  }

  @Transactional
  public void remove(String key, Long version) {
    var owner = account.requireId();
    version(version);
    var category = catalog.lock(owner, key);
    checkVersion(category, version);
    requireEmpty(category);
    if (category.isDefault())
      throw new RequestFailure(
          409, "DEFAULT_CATEGORY_PROTECTED", "Predefined categories cannot be removed.");
    catalog.remove(owner, key, version);
  }

  private void checkVersion(Category category, long version) {
    if (category.version() != version)
      throw new RequestFailure(
          409, "VERSION_CONFLICT", "Category changed; reload before updating.");
  }

  private void requireEmpty(Category category) {
    if (category.articleCount() > 0)
      throw new RequestFailure(
          409, "CATEGORY_IN_USE", "Remove or move the category's articles first.");
  }

  private void version(Long value) {
    if (value == null || value < 0) throw invalid();
  }

  private RequestFailure invalid() {
    return new RequestFailure(
        400, "INVALID_REQUEST", "Supply a non-negative version and fields to change.");
  }
}
