package com.know.identification.infrastructure.config;

import com.know.identification.application.*;
import com.know.identification.infrastructure.provider.SpringAiIdentificationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class IdentificationConfiguration {
  @Bean
  IdentificationPolicy identificationPolicy(IdentificationProperties p) {
    return new IdentificationPolicy(
        p.enabled(),
        p.providerOrder(),
        p.consecutiveFailureThreshold(),
        p.providerCooldown(),
        p.jobLease(),
        p.chunkCharacters(),
        p.maxChunks(),
        1000);
  }

  @Bean
  ArticleText identificationText() {
    return html -> html == null ? "" : org.jsoup.Jsoup.parse(html).text();
  }

  @Bean
  @Lazy
  IdentificationProvider openRouterIdentificationProvider(
      IdentificationProperties p, JsonMapper json) {
    return new SpringAiIdentificationProvider(
        "openrouter", "https://openrouter.ai/api/v1", p, json);
  }

  @Bean
  @Lazy
  IdentificationProvider groqIdentificationProvider(IdentificationProperties p, JsonMapper json) {
    return new SpringAiIdentificationProvider("groq", "https://api.groq.com/openai/v1", p, json);
  }
}
