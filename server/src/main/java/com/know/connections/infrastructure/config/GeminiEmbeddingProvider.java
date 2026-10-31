package com.know.connections.infrastructure.config;

import com.know.connections.application.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class GeminiEmbeddingProvider implements EmbeddingProvider {
  private final ConnectionProperties properties;
  private final JsonMapper json;
  private final Clock clock;
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public int countTokens(String text) {
    return request("countTokens", Map.of("contents", new Object[] {content(text)}))
        .path("totalTokens")
        .asInt(-1);
  }

  public float[] embed(String text, int dimensions) {
    JsonNode values =
        request(
                "embedContent",
                Map.of(
                    "model",
                    "models/" + properties.embedding().model(),
                    "content",
                    content(text),
                    "taskType",
                    "SEMANTIC_SIMILARITY",
                    "outputDimensionality",
                    dimensions))
            .path("embedding")
            .path("values");
    if (!values.isArray() || values.size() != dimensions) throw new EmbeddingFailure(502, null);
    float[] result = new float[dimensions];
    for (int i = 0; i < result.length; i++) {
      if (!values.get(i).isNumber()) throw new EmbeddingFailure(502, null);
      result[i] = values.get(i).floatValue();
    }
    return result;
  }

  private Map<String, Object> content(String text) {
    return Map.of("role", "user", "parts", new Object[] {Map.of("text", text)});
  }

  private JsonNode request(String operation, Object payload) {
    var config = properties.embedding();
    if (!config.liveRequestsEnabled() || !config.unbilledProjectConfirmed())
      throw new EmbeddingFailure(400, null);
    var request =
        HttpRequest.newBuilder(
                URI.create(
                    config.gemini().baseUrl()
                        + "/v1beta/models/"
                        + config.model()
                        + ":"
                        + operation))
            .timeout(config.requestTimeout())
            .header("x-goog-api-key", config.gemini().apiKey())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
            .build();
    try {
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200)
        throw new EmbeddingFailure(
            response.statusCode(),
            retryAfter(response.headers().firstValue("Retry-After").orElse(null)));
      try {
        return json.readTree(response.body());
      } catch (RuntimeException malformed) {
        throw new EmbeddingFailure(502, null);
      }
    } catch (java.io.IOException failure) {
      throw new EmbeddingFailure(0, null);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new EmbeddingFailure(0, null);
    }
  }

  private Instant retryAfter(String value) {
    if (value == null) return null;
    try {
      return clock.instant().plusSeconds(Math.max(0, Long.parseLong(value)));
    } catch (RuntimeException ignored) {
      try {
        return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
      } catch (RuntimeException malformed) {
        return null;
      }
    }
  }
}
