package com.know.entities.presentation;

import com.know.articles.application.ArticleSummary;
import com.know.entities.application.EntityModels.*;
import com.know.entities.presentation.EntityRequests.*;
import com.know.entities.presentation.EntityResponses.*;
import org.mapstruct.*;

@Mapper
public interface EntityApiMapper {
  Create toCommand(CreateEntityRequest request);

  @Mapping(target = "status", ignore = true)
  @Mapping(target = "completedAt", ignore = true)
  Create toCommand(AddArticleEntityRequest request);

  Edit toCommand(EditEntityRequest request);

  EntityResponse toResponse(Details entity);

  EntitySummaryResponse toResponse(Summary entity);

  CompletionResponse toResponse(Completion event);

  LinkedEntityResponse toResponse(LinkedEntity entity);

  EntityLinkResponse toResponse(Link link);

  AddEntityResponse toResponse(LinkResult result);

  AddCompletionResponse toResponse(CompletionResult result);

  CompletionYearResponse toResponse(Year year);

  SourceArticleResponse toResponse(ArticleSummary article);
}
