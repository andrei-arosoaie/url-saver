package com.know.shared.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("know.pagination")
public record PaginationProperties(
    @DefaultValue("20") int defaultLimit, @DefaultValue("100") int maxLimit)
    implements com.know.shared.application.PageLimits {
  public PaginationProperties {
    if (defaultLimit < 1 || maxLimit < defaultLimit || maxLimit > 1000)
      throw new IllegalArgumentException(
          "Pagination requires 1 <= default-limit <= max-limit <= 1000");
  }
}
