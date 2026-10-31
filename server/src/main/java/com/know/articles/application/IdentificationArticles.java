package com.know.articles.application;

import java.util.UUID;

/** Owned article metadata boundary. All background writes recheck manual ownership. */
public interface IdentificationArticles {
  record Input(
      UUID accountId,
      UUID id,
      String content,
      String category,
      String name,
      String categorySource,
      String nameSource) {
    public boolean categoryMissing() {
      return !categorySource.equals("USER");
    }

    public boolean nameMissing() {
      return !nameSource.equals("USER");
    }
  }

  Input read(UUID account, UUID article);

  boolean apply(Input snapshot, String category, String mainName);
}
