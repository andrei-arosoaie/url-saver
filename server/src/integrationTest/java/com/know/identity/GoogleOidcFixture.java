package com.know.identity;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.json.JsonMapper;

/** Disposable OIDC server: protocol and signature verification run without Google credentials. */
final class GoogleOidcFixture {
  private final HttpServer server;
  private final com.nimbusds.jose.jwk.RSAKey key;
  private final Map<String, String> nonces = new ConcurrentHashMap<>();
  private final JsonMapper json = JsonMapper.builder().build();

  GoogleOidcFixture() {
    try {
      key = new RSAKeyGenerator(3072).keyID("google-fixture").generate();
      server = HttpServer.create(new InetSocketAddress("0.0.0.0", 0), 0);
      server.createContext("/", this::handle);
      server.start();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  String url(String path) {
    return "http://localhost:" + server.getAddress().getPort() + path;
  }

  static Map<String, String> form(String raw) {
    Map<String, String> values = new HashMap<>();
    if (raw != null)
      for (String part : raw.split("&")) {
        var pair = part.split("=", 2);
        values.put(
            URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
            pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "");
      }
    return values;
  }

  private void handle(HttpExchange exchange) throws java.io.IOException {
    try {
      Object response;
      switch (exchange.getRequestURI().getPath()) {
        case "/authorize" -> {
          var params = form(exchange.getRequestURI().getRawQuery());
          var code = UUID.randomUUID().toString();
          nonces.put(code, params.get("nonce"));
          exchange
              .getResponseHeaders()
              .add(
                  "Location",
                  params.get("redirect_uri")
                      + "?code="
                      + code
                      + "&state="
                      + URLEncoder.encode(params.get("state"), StandardCharsets.UTF_8));
          exchange.sendResponseHeaders(302, -1);
          return;
        }
        case "/jwks" -> response = new JWKSet(key.toPublicJWK()).toJSONObject();
        case "/userinfo" ->
            response =
                Map.of(
                    "sub",
                    "protocol-user",
                    "email",
                    "protocol@example.test",
                    "email_verified",
                    true);
        case "/token" -> {
          var params =
              form(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          var nonce = nonces.remove(params.get("code"));
          if (nonce == null) {
            exchange.sendResponseHeaders(400, -1);
            return;
          }
          var now = Instant.now();
          var claims =
              new JWTClaimsSet.Builder()
                  .issuer("https://accounts.google.com")
                  .subject("protocol-user")
                  .audience("fixture-client")
                  .issueTime(Date.from(now))
                  .expirationTime(Date.from(now.plusSeconds(300)))
                  .claim("nonce", nonce)
                  .claim("email", "protocol@example.test")
                  .claim("email_verified", true)
                  .build();
          var jwt =
              new SignedJWT(
                  new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
          jwt.sign(new RSASSASigner(key));
          response =
              Map.of(
                  "access_token",
                  "fixture-provider-token",
                  "token_type",
                  "Bearer",
                  "expires_in",
                  300,
                  "scope",
                  "openid profile email",
                  "id_token",
                  jwt.serialize());
        }
        default -> {
          exchange.sendResponseHeaders(404, -1);
          return;
        }
      }
      var bytes = json.writeValueAsBytes(response);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);
      exchange.getResponseBody().write(bytes);
    } catch (Exception e) {
      exchange.sendResponseHeaders(500, -1);
    } finally {
      exchange.close();
    }
  }
}
