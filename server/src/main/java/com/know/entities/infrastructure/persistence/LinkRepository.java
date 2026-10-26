package com.know.entities.infrastructure.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface LinkRepository extends JpaRepository<ArticleEntityLink, ArticleEntityLink.Key> {
  Optional<ArticleEntityLink> findByAccountIdAndArticleIdAndEntityId(
      UUID owner, UUID article, UUID entity);
}
