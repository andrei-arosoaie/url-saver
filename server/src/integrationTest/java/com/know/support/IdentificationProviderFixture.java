package com.know.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import tools.jackson.databind.json.JsonMapper;

/** Exercises the real Spring AI SDK and HTTP protocol without spending live quota. */
public class IdentificationProviderFixture implements AutoCloseable {
  public record Reply(int status, String body, Runnable beforeReply) {}

  private final HttpServer server;
  private final ConcurrentLinkedQueue<Reply> openrouter = new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<Reply> groq = new ConcurrentLinkedQueue<>();
  public final List<String> openrouterRequests = new CopyOnWriteArrayList<>();
  public final List<String> groqRequests = new CopyOnWriteArrayList<>();
  private final JsonMapper json = JsonMapper.builder().build();

  public IdentificationProviderFixture() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/",
          exchange -> {
            boolean first = exchange.getRequestURI().getPath().startsWith("/openrouter/");
            var requests = first ? openrouterRequests : groqRequests;
            requests.add(
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var reply = (first ? openrouter : groq).poll();
            if (reply == null)
              reply =
                  new Reply(
                      503,
                      "{\"error\":{\"message\":\"Fixture has no response\",\"type\":\"server_error\"}}",
                      null);
            if (reply.beforeReply() != null) reply.beforeReply().run();
            var bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
          });
      server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
      server.start();
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
  }

  public String url(String provider) {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/" + provider + "/v1";
  }

  public void reset() {
    openrouter.clear();
    groq.clear();
    openrouterRequests.clear();
    groqRequests.clear();
  }

  public void fail(String provider, int code, String message) {
    queue(provider)
        .add(
            new Reply(
                code,
                json.writeValueAsString(
                    java.util.Map.of(
                        "error",
                        java.util.Map.of(
                            "message",
                            message,
                            "type",
                            "provider_error",
                            "code",
                            code == 429 ? "rate_limit_exceeded" : "server_error"))),
                null));
  }

  public void result(String provider, String result) {
    result(provider, result, null);
  }

  public void result(String provider, String result, Runnable beforeReply) {
    var response =
        java.util.Map.of(
            "id",
            "test-completion",
            "object",
            "chat.completion",
            "created",
            1,
            "model",
            "test-model",
            "choices",
            List.of(
                java.util.Map.of(
                    "index",
                    0,
                    "message",
                    java.util.Map.of("role", "assistant", "content", result),
                    "finish_reason",
                    "stop")),
            "usage",
            java.util.Map.of("prompt_tokens", 50, "completion_tokens", 20, "total_tokens", 70));
    queue(provider).add(new Reply(200, json.writeValueAsString(response), beforeReply));
  }

  private ConcurrentLinkedQueue<Reply> queue(String provider) {
    return provider.equals("openrouter") ? openrouter : groq;
  }

  public void close() {
    server.stop(0);
  }
}
