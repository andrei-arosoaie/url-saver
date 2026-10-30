package com.know.categories.application;

import java.util.List;
import java.util.UUID;

public interface CategoryCatalog {
  void ensureDefaults(UUID account);

  List<Category> list(UUID account);

  void requireEnabled(UUID account, String key);

  void lockEnabled(UUID account, String key);

  Category lock(UUID account, String key);

  Category create(UUID account, String name, String color);

  Category edit(UUID account, String key, long version, String name, String color, Boolean enabled);

  void remove(UUID account, String key, long version);
}
