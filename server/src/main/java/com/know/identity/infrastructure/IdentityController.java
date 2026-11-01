package com.know.identity.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "know.identity.enabled", havingValue = "true")
public class IdentityController {
  private final IdentityProperties properties;
  private final IdentityService service;

  public record Pending(
      String client, String redirect, String challenge, String state, long createdAt)
      implements java.io.Serializable {}

  @GetMapping("/oauth2/authorize")
  public void authorize(
      @RequestParam Map<String, String> params,
      HttpServletRequest request,
      HttpServletResponse response)
      throws java.io.IOException {
    String client = params.get("client_id"),
        redirect = params.get("redirect_uri"),
        challenge = params.get("code_challenge"),
        state = params.get("state");
    if (!properties.validClient(client, redirect)
        || !"code".equals(params.get("response_type"))
        || !"S256".equals(params.get("code_challenge_method"))
        || challenge == null
        || !challenge.matches("[A-Za-z0-9_-]{43}")
        || state == null
        || !state.matches("[A-Za-z0-9_-]{32,128}")) throw new OAuthFailure("invalid_request");
    request
        .getSession()
        .setAttribute(
            "know.authorization",
            new Pending(client, redirect, challenge, state, System.currentTimeMillis()));
    response.setHeader("Cache-Control", "no-store");
    response.sendRedirect("/oauth2/authorization/google");
  }

  private String refreshCookie() {
    return properties.cookie().secure() ? "__Host-know-refresh" : "know-refresh";
  }

  private String csrfCookie() {
    return properties.cookie().secure() ? "__Host-know-csrf" : "know-csrf";
  }

  private String cookie(HttpServletRequest request, String name) {
    if (request.getCookies() != null)
      for (var c : request.getCookies()) if (name.equals(c.getName())) return c.getValue();
    return null;
  }

  private void setCookie(
      HttpServletResponse response, String name, String value, boolean httpOnly, long seconds) {
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(name, value)
            .path("/")
            .secure(properties.cookie().secure())
            .httpOnly(httpOnly)
            .sameSite("Lax")
            .maxAge(seconds)
            .build()
            .toString());
  }

  @GetMapping("/oauth2/csrf")
  public Map<String, String> csrf(HttpServletRequest request, HttpServletResponse response) {
    String origin = request.getHeader("Origin");
    if (origin != null && !properties.webOrigin().equals(origin))
      throw new OAuthFailure("invalid_request");
    String token = cookie(request, csrfCookie());
    if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) token = IdentityService.randomToken();
    setCookie(response, csrfCookie(), token, false, properties.refreshWindow().toSeconds());
    response.setHeader("Cache-Control", "no-store");
    return Map.of("csrf_token", token);
  }

  private void verifyWeb(HttpServletRequest request) {
    String csrf = cookie(request, csrfCookie());
    if (!properties.webOrigin().equals(request.getHeader("Origin"))
        || csrf == null
        || !csrf.equals(request.getHeader("X-Know-CSRF")))
      throw new OAuthFailure("invalid_request");
  }

  @PostMapping(value = "/oauth2/token", consumes = "application/x-www-form-urlencoded")
  public Map<String, Object> token(
      @RequestParam Map<String, String> params,
      HttpServletRequest request,
      HttpServletResponse response) {
    String client = params.get("client_id");
    if (!properties.web(client) && !properties.extensionClientId().equals(client))
      throw new OAuthFailure("invalid_client");
    if (properties.web(client)) {
      verifyWeb(request);
      service.verifySessionBinding(
          cookie(request, refreshCookie()), client, params.get("expected_session"));
    }
    IdentityService.Tokens tokens =
        switch (params.getOrDefault("grant_type", "")) {
          case "authorization_code" ->
              service.exchange(
                  params.get("code"),
                  client,
                  params.get("redirect_uri"),
                  params.get("code_verifier"));
          case "refresh_token" ->
              service.refresh(
                  properties.web(client)
                      ? cookie(request, refreshCookie())
                      : params.get("refresh_token"),
                  client);
          default -> throw new OAuthFailure("unsupported_grant_type");
        };
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("Pragma", "no-cache");
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("access_token", tokens.accessToken());
    result.put("token_type", "Bearer");
    result.put("expires_in", tokens.expiresIn());
    if (properties.web(client))
      setCookie(
          response,
          refreshCookie(),
          tokens.refreshToken(),
          true,
          properties.refreshWindow().toSeconds());
    else result.put("refresh_token", tokens.refreshToken());
    return result;
  }

  @PostMapping(value = "/oauth2/logout", consumes = "application/x-www-form-urlencoded")
  public ResponseEntity<Void> logout(
      @RequestParam Map<String, String> params,
      HttpServletRequest request,
      HttpServletResponse response) {
    String client = params.get("client_id");
    if (properties.web(client)) {
      verifyWeb(request);
      service.verifySessionBinding(
          cookie(request, refreshCookie()), client, params.get("expected_session"));
      service.logoutRefresh(cookie(request, refreshCookie()), client);
      setCookie(response, refreshCookie(), "", true, 0);
      setCookie(response, csrfCookie(), "", false, 0);
    } else if (properties.extensionClientId().equals(client))
      service.logoutRefresh(params.get("refresh_token"), client);
    else throw new OAuthFailure("invalid_client");
    return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
  }

  @ExceptionHandler(OAuthFailure.class)
  public ResponseEntity<Map<String, String>> failure(OAuthFailure failure) {
    return ResponseEntity.badRequest()
        .header("Cache-Control", "no-store")
        .body(Map.of("error", failure.getMessage()));
  }
}
