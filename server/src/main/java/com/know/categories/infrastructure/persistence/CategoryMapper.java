package com.know.categories.infrastructure.persistence;

import com.know.categories.application.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface CategoryMapper {
  @Mapping(target = "articleCount", source = "count")
  @Mapping(target = "isDefault", source = "entity.default")
  Category toRecord(CategoryEntity entity, long count);
}
