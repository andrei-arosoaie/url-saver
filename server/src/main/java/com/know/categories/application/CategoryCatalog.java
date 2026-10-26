package com.know.categories.application;

import java.util.List;
import java.util.UUID;

public interface CategoryCatalog {
  void ensureDefaults(UUID account);

  List<Category> list(UUID account);

  void requireEnabled(UUID account, String key);
}
