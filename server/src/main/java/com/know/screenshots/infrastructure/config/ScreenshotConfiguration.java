package com.know.screenshots.infrastructure.config;

import com.know.screenshots.application.*;
import com.know.screenshots.infrastructure.imaging.NativePngTransformation;
import com.know.screenshots.infrastructure.storage.ImageKitStorage;
import io.imagekit.client.ImageKitClient;
import io.imagekit.client.okhttp.ImageKitOkHttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class ScreenshotConfiguration {
  @Bean
  public ScreenshotPolicy screenshotPolicy(ScreenshotProperties p) {
    return new ScreenshotPolicy(
        p.format(), p.jpegQuality(), p.maxUploadBytes(), p.maxStoredBytes(), p.maxDecodedPixels());
  }

  @Bean
  public ImageTransformation screenshotTransformation(ScreenshotPolicy policy) {
    return new NativePngTransformation(policy);
  }

  @Bean
  @Lazy
  public ImageKitClient imageKitClient(ScreenshotProperties properties) {
    if (properties.imagekit().privateKey().isBlank())
      throw new IllegalStateException("ImageKit credentials are not configured.");
    var builder =
        ImageKitOkHttpClient.builder()
            .privateKey(properties.imagekit().privateKey())
            .timeout(Duration.ofSeconds(30))
            .maxRetries(0);
    // An isolated provider endpoint lets client integration tests exercise the
    // actual SDK and multipart transport without live credentials or files.
    if (!properties.imagekit().apiBaseUrl().isBlank())
      builder.baseUrl(properties.imagekit().apiBaseUrl());
    return builder.build();
  }

  @Bean
  public ScreenshotStorage imageKitStorage(
      @Lazy ImageKitClient client, ScreenshotProperties properties) {
    return new ImageKitStorage(client, properties.imagekit().urlEndpoint(), Clock.systemUTC());
  }

  @Bean
  public ScreenshotService screenshotService(
      com.know.articles.application.ArticleQueries articles,
      com.know.accounts.application.CurrentAccount account,
      ScreenshotStore store,
      ImageTransformation transformation,
      java.util.List<ScreenshotStorage> providers,
      ScreenshotProperties properties) {
    return new ScreenshotService(
        articles,
        account,
        store,
        transformation,
        providers,
        properties.provider(),
        properties.signedUrlTtl(),
        Clock.systemUTC(),
        properties.signedUrlCacheEntries());
  }
}
