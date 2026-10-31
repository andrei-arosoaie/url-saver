package com.know.connections.presentation;

import com.know.connections.application.ConnectionModels;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ConnectionApiMapper {
  ConnectionPageResponse toResponse(ConnectionModels.Page page);
}
