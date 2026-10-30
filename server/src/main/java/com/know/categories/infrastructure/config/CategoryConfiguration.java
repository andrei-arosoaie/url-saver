package com.know.categories.infrastructure.config;

import com.know.categories.application.CategoryRules;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.*;
import org.springframework.validation.annotation.Validated;

@Configuration
public class CategoryConfiguration {
  @ConfigurationProperties("know.categories")
  @Validated
  public record Properties(@DefaultValue("100") @Positive int maxNameCodePoints) {}

  @Bean
  CategoryRules categoryRules(Properties properties) {
    return new CategoryRules(properties.maxNameCodePoints());
  }
}
