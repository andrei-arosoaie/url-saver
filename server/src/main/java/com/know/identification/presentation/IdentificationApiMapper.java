package com.know.identification.presentation;

import com.know.identification.application.Identification;
import org.mapstruct.Mapper;

@Mapper
public interface IdentificationApiMapper {
  IdentificationResponse toResponse(Identification.Status status);

  IdentificationResponse.SubjectResponse toResponse(Identification.Subject subject);
}
