package com.know.accounts.infrastructure.config;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("know.temporary-account")
public record TemporaryAccountProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("00000000-0000-4000-8000-000000000001") UUID id) {}
