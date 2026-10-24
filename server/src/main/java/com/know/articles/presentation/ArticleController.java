package com.know.articles.presentation;

import com.know.articles.application.*;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/articles")
@RequiredArgsConstructor
public class ArticleController {
  private final ArticleCommands commands;
  private final ArticleQueries queries;
  private final ArticleApiMapper mapper;

  @GetMapping("/{id}")
  public ArticleResponse detail(@PathVariable UUID id) {
    return mapper.toResponse(queries.detail(id));
  }

  @PostMapping
  public ResponseEntity<SaveArticleResponse> save(@RequestBody SaveArticleRequest request) {
    var result = commands.save(mapper.toCommand(request));
    var response = mapper.toResponse(result);
    return result.outcome() == SaveResult.Outcome.CREATED
        ? ResponseEntity.created(URI.create("/api/v1/articles/" + result.article().id()))
            .body(response)
        : ResponseEntity.ok(response);
  }

  @PatchMapping("/{id}")
  public ArticleResponse update(@PathVariable UUID id, @RequestBody UpdateArticleRequest request) {
    return mapper.toResponse(commands.update(id, mapper.toCommand(request)));
  }
}
