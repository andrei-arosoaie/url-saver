package com.know.screenshots.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("know.screenshots")
@Validated
public record ScreenshotProperties(
    @DefaultValue("imagekit") @NotBlank String provider,
    @DefaultValue("jpeg") @Pattern(regexp = "jpeg|png") String format,
    @DefaultValue("90") @Min(1) @Max(100) int jpegQuality,
    @DefaultValue("20971520") @Positive long maxUploadBytes,
    @DefaultValue("5242880") @Positive long maxStoredBytes,
    @DefaultValue("16777216") @Positive long maxDecodedPixels,
    @DefaultValue("30m") Duration signedUrlTtl,
    @DefaultValue @Valid ImageKit imagekit) {
  public record ImageKit(
      @DefaultValue("") String privateKey,
      @DefaultValue("") String urlEndpoint,
      @DefaultValue("") String apiBaseUrl) {
    @Override
    public String toString() {
      return "ImageKit[credentials=redacted]";
    }
  }

  public ScreenshotProperties {
    if (signedUrlTtl == null || signedUrlTtl.isNegative() || signedUrlTtl.isZero())
      throw new IllegalArgumentException("Screenshot URL lifetime must be positive.");
  }
}
