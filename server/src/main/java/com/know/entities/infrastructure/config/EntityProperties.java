package com.know.entities.infrastructure.config;

import com.know.entities.application.EntitySettings;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("know.entities")
public record EntityProperties(@DefaultValue("1000") @Positive int maxTitleCodePoints)
    implements EntitySettings {}
