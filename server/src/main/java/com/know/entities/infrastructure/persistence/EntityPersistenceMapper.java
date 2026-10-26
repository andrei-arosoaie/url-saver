package com.know.entities.infrastructure.persistence;

import static com.know.entities.application.EntityModels.*;

import java.time.Instant;
import org.mapstruct.*;

@Mapper
interface EntityPersistenceMapper {
  @Mapping(target = "lastCompletedAt", source = "last")
  @Mapping(target = "totalCompletionCount", source = "count")
  Details details(TrackedEntity entity, Instant last, long count);

  @Mapping(target = "completionCount", source = "count")
  @Mapping(target = "latestMatchingCompletionAt", source = "latest")
  Summary summary(Details details, long count, Instant latest);

  @Mapping(target = "linkedAt", source = "time")
  LinkedEntity linked(Details details, Instant time);

  Completion completion(CompletionEntity entity);

  Link link(ArticleEntityLink entity);
}
