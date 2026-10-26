package com.know.categories.presentation;

import com.know.accounts.application.CurrentAccount;
import com.know.categories.application.CategoryCatalog;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.mapstruct.Mapper;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {
  private final CurrentAccount current;
  private final CategoryCatalog catalog;
  private final CategoryApiMapper mapper;

  public record CategoryResponse(
      String key,
      String displayName,
      String color,
      boolean isDefault,
      boolean enabled,
      Instant createdAt,
      long version) {}

  public record CatalogResponse(List<CategoryResponse> items) {}

  @GetMapping
  public CatalogResponse list() {
    var account = current.requireId();
    catalog.ensureDefaults(account);
    return new CatalogResponse(catalog.list(account).stream().map(mapper::toResponse).toList());
  }

  @Mapper
  public interface CategoryApiMapper {
    CategoryResponse toResponse(com.know.categories.application.Category category);
  }
}
