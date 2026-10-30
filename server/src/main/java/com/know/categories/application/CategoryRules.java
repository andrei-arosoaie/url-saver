package com.know.categories.application;

import com.know.shared.application.RequestFailure;

public record CategoryRules(int maxNameCodePoints) {
  public String name(String value) {
    if (value == null) throw invalid("Category name is required.");
    var clean = value.strip();
    if (clean.isBlank() || clean.codePointCount(0, clean.length()) > maxNameCodePoints)
      throw invalid("Category name must contain 1 to " + maxNameCodePoints + " characters.");
    return clean;
  }

  public void color(String value) {
    if (value == null || !value.matches("#[0-9a-fA-F]{6}"))
      throw invalid("Category color must use #RRGGBB.");
  }

  private RequestFailure invalid(String message) {
    return new RequestFailure(400, "INVALID_REQUEST", message);
  }
}
