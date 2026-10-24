package com.know.articles.presentation;

import com.know.articles.application.*;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ArticleApiMapper {
  SaveArticle toCommand(SaveArticleRequest request);

  UpdateArticle toCommand(UpdateArticleRequest request);

  ArticleResponse toResponse(ArticleSnapshot article);

  SaveArticleResponse toResponse(SaveResult result);
}
