package com.know.categories.infrastructure.persistence;

import com.know.categories.application.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface CategoryMapper {
  @Mapping(target = "isDefault", source = "default")
  Category toRecord(CategoryEntity entity);
}
