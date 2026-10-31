package com.know.identification.presentation;

import com.know.identification.application.IdentificationService;
import com.know.shared.presentation.EntityApiErrors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(
    value = "/api/v1/articles/{articleId}/identification",
    produces = "application/json")
@Tag(name = "Identification")
@EntityApiErrors
@RequiredArgsConstructor
public class IdentificationController {
  private final IdentificationService service;
  private final IdentificationApiMapper mapper;

  @GetMapping
  @Operation(
      operationId = "getArticleIdentification",
      summary = "Read identification status, provenance and subjects",
      description =
          "Owned articles only; missing and foreign articles both return 404. Exactly one main subject when subjects exist. USER fields are never overwritten. No provider credentials or raw errors exposed.")
  public IdentificationResponse status(@PathVariable UUID articleId) {
    return mapper.toResponse(service.status(articleId));
  }

  @PostMapping
  @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
  @Operation(
      operationId = "requestArticleIdentification",
      summary = "Request background identification",
      description =
          "Returns 202. Reuses pending/running work; a completed attempt can be requested again. Preserves manual fields and entity links. No automatic retry after both providers fail; providers in cooldown remain unavailable. Returns 503 IDENTIFICATION_DISABLED when disabled.")
  public ResponseEntity<IdentificationResponse> request(@PathVariable UUID articleId) {
    return ResponseEntity.accepted().body(mapper.toResponse(service.request(articleId)));
  }
}
