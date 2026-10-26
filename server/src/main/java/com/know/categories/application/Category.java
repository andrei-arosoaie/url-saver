package com.know.categories.application;

import java.time.Instant;

public record Category(
    String key,
    String displayName,
    String color,
    boolean isDefault,
    boolean enabled,
    Instant createdAt,
    long version) {}
