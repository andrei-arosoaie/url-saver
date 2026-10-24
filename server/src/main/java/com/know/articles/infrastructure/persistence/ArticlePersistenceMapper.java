package com.know.articles.infrastructure.persistence;

import com.know.articles.application.ArticleSnapshot;
import com.know.articles.application.ArticleSummary;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
interface ArticlePersistenceMapper {
  ArticleSnapshot toSnapshot(ArticleEntity entity);

  ArticleSummary toSummary(ArticleSummaryRow row);
}
