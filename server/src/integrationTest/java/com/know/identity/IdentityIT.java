package com.know.identity;

import static org.assertj.core.api.Assertions.*;

import com.know.KnowApplication;
import com.know.identity.infrastructure.IdentityService;
import com.know.identity.infrastructure.OAuthFailure;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
    classes = KnowApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.config.location=classpath:application-integration.yml",
      "know.identity.enabled=true",
      "know.identification.enabled=false",
      "know.connections.enabled=false",
      "know.temporary-account.enabled=false"
    })
@Import(IdentityIT.Providers.class)
class IdentityIT {
  @org.springframework.boot.test.context.TestConfiguration
  static class Providers {
    @org.springframework.context.annotation.Bean(destroyMethod = "close")
    com.know.support.ScreenshotProviderFixture screenshotFixture() throws java.io.IOException {
      return new com.know.support.ScreenshotProviderFixture();
    }

    @org.springframework.context.annotation.Bean
    com.know.screenshots.application.ScreenshotStorage testStorage(
        com.know.support.ScreenshotProviderFixture fixture) {
      return fixture.storage();
    }
  }

  static final PostgreSQLContainer DATABASE =
      new PostgreSQLContainer(
          org.testcontainers.utility.DockerImageName.parse("pgvector/pgvector:0.8.7-pg17")
              .asCompatibleSubstituteFor("postgres"));
  static final UUID LEGACY = UUID.fromString("00000000-0000-4000-8000-000000000001");
  static final Path KEYS = keys();
  static final GoogleOidcFixture GOOGLE = new GoogleOidcFixture();

  static {
    DATABASE.start();
  }

  static Path keys() {
    try {
      var dir = Files.createTempDirectory("know-identity-it");
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(3072);
      var pair = generator.generateKeyPair();
      Files.writeString(
          dir.resolve("private.pem"),
          "-----BEGIN PRIVATE KEY-----\n"
              + Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())
              + "\n-----END PRIVATE KEY-----");
      Files.writeString(
          dir.resolve("public.pem"),
          "-----BEGIN PUBLIC KEY-----\n"
              + Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())
              + "\n-----END PUBLIC KEY-----");
      return dir;
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void configuration(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", DATABASE::getJdbcUrl);
    r.add("spring.datasource.username", DATABASE::getUsername);
    r.add("spring.datasource.password", DATABASE::getPassword);
    Map.ofEntries(
            Map.entry("issuer", "http://localhost:8080"),
            Map.entry("web-origin", "http://localhost:5173"),
            Map.entry("extension-client-id", "know-firefox"),
            Map.entry("extension-redirect-uri", "https://fixture.extensions.allizom.org/"),
            Map.entry("web-redirect-uri", "http://localhost:5173/auth/callback"),
            Map.entry("signing.key-id", "test-1"),
            Map.entry("signing.private-key", KEYS.resolve("private.pem").toUri().toString()),
            Map.entry("signing.public-key", KEYS.resolve("public.pem").toUri().toString()),
            Map.entry("access-token-lifetime", "24h"),
            Map.entry("refresh-window", "24h"),
            Map.entry("refresh-expiry-mode", "from-sign-in"),
            Map.entry("cookie.secure", "false"),
            Map.entry("cookie.same-site", "Lax"),
            Map.entry("legacy-account.id", LEGACY.toString()),
            Map.entry("legacy-account.owner-email", "owner@example.test"))
        .forEach((key, value) -> r.add("know.identity." + key, () -> value));
    r.add("spring.security.oauth2.client.registration.google.client-id", () -> "fixture-client");
    r.add(
        "spring.security.oauth2.client.registration.google.client-secret", () -> "fixture-secret");
    r.add("spring.security.oauth2.client.registration.google.scope", () -> "openid,profile,email");
    r.add(
        "spring.security.oauth2.client.registration.google.redirect-uri",
        () -> "{baseUrl}/login/oauth2/code/{registrationId}");
    r.add(
        "spring.security.oauth2.client.provider.google.authorization-uri",
        () -> GOOGLE.url("/authorize"));
    r.add("spring.security.oauth2.client.provider.google.token-uri", () -> GOOGLE.url("/token"));
    r.add("spring.security.oauth2.client.provider.google.jwk-set-uri", () -> GOOGLE.url("/jwks"));
    r.add(
        "spring.security.oauth2.client.provider.google.user-info-uri",
        () -> GOOGLE.url("/userinfo"));
  }

  @Autowired IdentityService service;
  @Autowired JdbcTemplate jdbc;
  @Autowired JwtDecoder decoder;
  @LocalServerPort int port;
  final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
  final String redirect = "https://fixture.extensions.allizom.org/";

  HttpResponse<String> get(String path, String bearer) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port).resolve(path));
    if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
    return http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  IdentityService.Tokens signIn(String subject) {
    var account =
        service.resolveGoogle(
            "https://accounts.google.com", subject, subject + "@example.test", true);
    String verifier = IdentityService.randomToken();
    return service.exchange(
        service.authorize(account, "know-firefox", redirect, IdentityService.hash(verifier)),
        "know-firefox",
        redirect,
        verifier);
  }

  @Test
  void googleOidcRoundTripValidatesProviderSignatureAndReturnsOnlyAPkceCode() throws Exception {
    String verifier = IdentityService.randomToken(), state = IdentityService.randomToken();
    var params = new java.util.LinkedHashMap<String, String>();
    params.put("client_id", "know-firefox");
    params.put("redirect_uri", redirect);
    params.put("response_type", "code");
    params.put("code_challenge", IdentityService.hash(verifier));
    params.put("code_challenge_method", "S256");
    params.put("state", state);
    String query =
        params.entrySet().stream()
            .map(
                e ->
                    e.getKey()
                        + "="
                        + java.net.URLEncoder.encode(
                            e.getValue(), java.nio.charset.StandardCharsets.UTF_8))
            .collect(java.util.stream.Collectors.joining("&"));
    var response = get("/oauth2/authorize?" + query, null);
    for (int i = 0; i < 3; i++) {
      assertThat(response.statusCode()).isEqualTo(302);
      URI next =
          URI.create("http://localhost:" + port)
              .resolve(response.headers().firstValue("Location").orElseThrow());
      response =
          http.send(
              HttpRequest.newBuilder(next).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    assertThat(response.statusCode()).isEqualTo(302);
    var callback = URI.create(response.headers().firstValue("Location").orElseThrow());
    assertThat(callback.getHost()).isEqualTo("fixture.extensions.allizom.org");
    var returned = GoogleOidcFixture.form(callback.getRawQuery());
    assertThat(returned.get("state")).isEqualTo(state);
    assertThat(returned).doesNotContainKeys("access_token", "refresh_token");
    var tokens = service.exchange(returned.get("code"), "know-firefox", redirect, verifier);
    assertThat(get("/api/v1/articles", tokens.accessToken()).statusCode()).isEqualTo(200);
    assertThat(get("/api/v1/identity/profile", tokens.accessToken()).body())
        .contains("Protocol User", "https://lh3.googleusercontent.com/protocol-photo");
    // A Google login session cookie alone never grants library access.
    assertThat(get("/api/v1/articles", null).statusCode()).isEqualTo(401);
  }

  @Test
  void webTransportRequiresCsrfAndRotatesHttpOnlyRefreshCookieWithoutExposingItInJson()
      throws Exception {
    var account =
        service.resolveGoogle("https://accounts.google.com", "web", "web@example.test", true);
    String verifier = IdentityService.randomToken();
    String code =
        service.authorize(
            account,
            "know-web",
            "http://localhost:5173/auth/callback",
            IdentityService.hash(verifier));
    var json = tools.jackson.databind.json.JsonMapper.builder().build();
    var csrf = get("/oauth2/csrf", null);
    String csrfToken = json.readTree(csrf.body()).get("csrf_token").asString();
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/oauth2/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Origin", "http://localhost:5173")
            .header("X-Know-CSRF", csrfToken)
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "client_id=know-web&grant_type=authorization_code&redirect_uri=http%3A%2F%2Flocalhost%3A5173%2Fauth%2Fcallback&code="
                        + code
                        + "&code_verifier="
                        + verifier))
            .build();
    var response = http.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).doesNotContain("refresh_token");
    assertThat(response.headers().allValues("Set-Cookie"))
        .anySatisfy(
            value ->
                assertThat(value).contains("know-refresh=", "HttpOnly", "SameSite=Lax", "Path=/"));
    var jwt = decoder.decode(json.readTree(response.body()).get("access_token").asString());
    for (String route : List.of("/oauth2/token", "/oauth2/logout")) {
      var changedSession =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + route))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .header("Origin", "http://localhost:5173")
              .header("X-Know-CSRF", csrfToken)
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      "client_id=know-web&grant_type=refresh_token&expected_session="
                          + UUID.randomUUID()))
              .build();
      var denied = http.send(changedSession, HttpResponse.BodyHandlers.ofString());
      assertThat(denied.statusCode()).isEqualTo(400);
      assertThat(denied.headers().allValues("Set-Cookie")).isEmpty();
    }
    var refresh =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/oauth2/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Origin", "http://localhost:5173")
            .header("X-Know-CSRF", csrfToken)
            .POST(
                HttpRequest.BodyPublishers.ofString("client_id=know-web&grant_type=refresh_token"))
            .build();
    var rotated = http.send(refresh, HttpResponse.BodyHandlers.ofString());
    assertThat(rotated.statusCode()).isEqualTo(200);
    assertThat(rotated.body()).doesNotContain("refresh_token");
    assertThat(rotated.headers().allValues("Set-Cookie"))
        .isNotEqualTo(response.headers().allValues("Set-Cookie"));
    var logout =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/oauth2/logout"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Origin", "http://localhost:5173")
            .header("X-Know-CSRF", csrfToken)
            .POST(HttpRequest.BodyPublishers.ofString("client_id=know-web"))
            .build();
    assertThat(http.send(logout, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(204);
    assertThat(service.active(UUID.fromString(jwt.getClaimAsString("sid")), account)).isFalse();
  }

  @Test
  void providerDenialReturnsToTheValidatedExtensionCallbackWithOriginalState() throws Exception {
    String state = IdentityService.randomToken(), verifier = IdentityService.randomToken();
    var start =
        get(
            "/oauth2/authorize?client_id=know-firefox&redirect_uri="
                + java.net.URLEncoder.encode(redirect, java.nio.charset.StandardCharsets.UTF_8)
                + "&response_type=code&code_challenge_method=S256&code_challenge="
                + IdentityService.hash(verifier)
                + "&state="
                + state,
            null);
    var login = get(start.headers().firstValue("Location").orElseThrow(), null);
    var provider = URI.create(login.headers().firstValue("Location").orElseThrow());
    var googleState = GoogleOidcFixture.form(provider.getRawQuery()).get("state");
    var denied =
        get(
            "/login/oauth2/code/google?error=access_denied&state="
                + java.net.URLEncoder.encode(googleState, java.nio.charset.StandardCharsets.UTF_8),
            null);
    assertThat(denied.statusCode()).isEqualTo(302);
    var callback = URI.create(denied.headers().firstValue("Location").orElseThrow());
    assertThat(callback.getHost()).isEqualTo("fixture.extensions.allizom.org");
    assertThat(GoogleOidcFixture.form(callback.getRawQuery()))
        .containsEntry("state", state)
        .containsEntry("error", "access_denied")
        .doesNotContainKey("code");
  }

  @Test
  void verifiedOwnerClaimsLegacyExactlyOnceAndOtherIdentitiesGetIndependentAccounts() {
    jdbc.update(
        "insert into accounts(id,created_at) values (?,now()) on conflict do nothing", LEGACY);
    assertThatThrownBy(
            () ->
                service.resolveGoogle(
                    "https://accounts.google.com", "unverified", "owner@example.test", false))
        .isInstanceOf(OAuthFailure.class);
    assertThatThrownBy(
            () ->
                service.resolveGoogle(
                    "https://attacker.test", "attacker", "owner@example.test", true))
        .isInstanceOf(OAuthFailure.class);
    assertThat(
            service.resolveGoogle(
                "https://accounts.google.com", "owner-subject", "owner@example.test", true))
        .isEqualTo(LEGACY);
    assertThat(
            service.resolveGoogle(
                "https://accounts.google.com", "owner-subject", "renamed@example.test", true))
        .isEqualTo(LEGACY);
    assertThat(
            service.resolveGoogle(
                "https://accounts.google.com", "new-subject", "owner@example.test", true))
        .isNotEqualTo(LEGACY);
  }

  @Test
  void profileUsesVerifiedGoogleClaimsAndRemovalIsAccountScopedAndSurvivesLaterSignIns()
      throws Exception {
    var account =
        service.resolveGoogle(
            "https://accounts.google.com",
            "profile-owner",
            "profile@example.test",
            true,
            "Andrei Example",
            "https://lh3.googleusercontent.com/photo");
    var tokens = signInProfile(account);
    var profile = get("/api/v1/identity/profile", tokens.accessToken());
    assertThat(profile.statusCode()).isEqualTo(200);
    assertThat(profile.body())
        .contains("Andrei Example", "https://lh3.googleusercontent.com/photo");
    assertThat(profile.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    var other =
        service.resolveGoogle(
            "https://accounts.google.com",
            "profile-other",
            "other@example.test",
            true,
            "Other User",
            "https://lh3.googleusercontent.com/other");
    var remove =
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/identity/profile/picture"))
            .header("Authorization", "Bearer " + tokens.accessToken())
            .DELETE()
            .build();
    var removed = http.send(remove, HttpResponse.BodyHandlers.ofString());
    assertThat(removed.statusCode()).isEqualTo(200);
    assertThat(removed.body()).contains("\"pictureUrl\":null", "\"pictureRemoved\":true");
    var restore =
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/identity/profile/picture"))
            .header("Authorization", "Bearer " + tokens.accessToken())
            .PUT(HttpRequest.BodyPublishers.noBody())
            .build();
    var restored = http.send(restore, HttpResponse.BodyHandlers.ofString());
    assertThat(restored.statusCode()).isEqualTo(200);
    assertThat(restored.body())
        .contains("https://lh3.googleusercontent.com/photo", "\"pictureRemoved\":false");
    service.removePicture(account);
    service.resolveGoogle(
        "https://accounts.google.com",
        "profile-owner",
        "profile@example.test",
        true,
        "Andrei Renamed",
        "https://lh3.googleusercontent.com/new-photo");
    assertThat(service.profile(account))
        .isEqualTo(new IdentityService.Profile("Andrei Renamed", null, true));
    var restoredLatest = http.send(restore, HttpResponse.BodyHandlers.ofString());
    assertThat(restoredLatest.statusCode()).isEqualTo(200);
    assertThat(restoredLatest.body())
        .contains("https://lh3.googleusercontent.com/new-photo", "\"pictureRemoved\":false");
    assertThat(service.profile(other).pictureUrl())
        .isEqualTo("https://lh3.googleusercontent.com/other");
    assertThat(get("/api/v1/identity/profile", null).statusCode()).isEqualTo(401);
    service.resolveGoogle(
        "https://accounts.google.com",
        "profile-url",
        "url@example.test",
        true,
        "Unsafe URL",
        "https://attacker.test/photo");
    var unsafe =
        service.resolveGoogle(
            "https://accounts.google.com",
            "profile-url",
            "url@example.test",
            true,
            "Unsafe URL",
            "javascript:alert(1)");
    assertThat(service.profile(unsafe).pictureUrl()).isNull();
  }

  private IdentityService.Tokens signInProfile(UUID account) {
    var verifier = IdentityService.randomToken();
    return service.exchange(
        service.authorize(account, "know-firefox", redirect, IdentityService.hash(verifier)),
        "know-firefox",
        redirect,
        verifier);
  }

  @Test
  void codesBindClientCallbackAndPkceAndCanOnlyBeRedeemedOnce() {
    var account =
        service.resolveGoogle("https://accounts.google.com", "pkce", "pkce@example.test", true);
    var verifier = IdentityService.randomToken();
    var code = service.authorize(account, "know-firefox", redirect, IdentityService.hash(verifier));
    assertThatThrownBy(
            () ->
                service.exchange(code, "know-web", "http://localhost:5173/auth/callback", verifier))
        .isInstanceOf(OAuthFailure.class);
    assertThatThrownBy(
            () -> service.exchange(code, "know-firefox", redirect, IdentityService.randomToken()))
        .isInstanceOf(OAuthFailure.class);
    var tokens = service.exchange(code, "know-firefox", redirect, verifier);
    assertThat(decoder.decode(tokens.accessToken()).getSubject()).isEqualTo(account.toString());
    assertThatThrownBy(() -> service.exchange(code, "know-firefox", redirect, verifier))
        .isInstanceOf(OAuthFailure.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from identity_refresh_tokens where token_hash=?",
                Integer.class,
                tokens.refreshToken()))
        .isZero();
  }

  @Test
  void rotationReuseRevokesOnlyThatSessionIncludingAlreadyIssuedAccessTokens() throws Exception {
    var first = signIn("rotation");
    var independent = signIn("rotation");
    var rotated = service.refresh(first.refreshToken(), "know-firefox");
    assertThat(get("/api/v1/articles", rotated.accessToken()).statusCode()).isEqualTo(200);
    assertThatThrownBy(() -> service.refresh(first.refreshToken(), "know-firefox"))
        .isInstanceOf(OAuthFailure.class);
    assertThat(get("/api/v1/articles", rotated.accessToken()).statusCode()).isEqualTo(401);
    assertThat(get("/api/v1/articles", first.accessToken()).statusCode()).isEqualTo(401);
    assertThat(get("/api/v1/articles", independent.accessToken()).statusCode()).isEqualTo(200);
    service.logoutRefresh(independent.refreshToken(), "know-firefox");
    assertThat(get("/api/v1/articles", independent.accessToken()).statusCode()).isEqualTo(401);
  }

  @Test
  void concurrentRefreshDetectsReuseAndCannotCreateTwoActiveSuccessors() throws Exception {
    var first = signIn("concurrent");
    try (var executor = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      Callable<Boolean> refresh =
          () -> {
            start.await();
            try {
              service.refresh(first.refreshToken(), "know-firefox");
              return true;
            } catch (OAuthFailure e) {
              return false;
            }
          };
      var a = executor.submit(refresh);
      var b = executor.submit(refresh);
      start.countDown();
      assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
      assertThat(get("/api/v1/articles", first.accessToken()).statusCode()).isEqualTo(401);
    }
  }

  @Test
  void publicEndpointsDoNotExposeLibraryAndRejectUnregisteredRedirectsAndCookieCsrf()
      throws Exception {
    assertThat(get("/api/v1/articles", null).statusCode()).isEqualTo(401);
    assertThat(get("/api/v1/articles", "invalid").statusCode()).isEqualTo(401);
    assertThat(get("/", null).statusCode()).isEqualTo(200);
    var authorize =
        get(
            "/oauth2/authorize?client_id=know-web&redirect_uri=https://attacker.test/&response_type=code",
            null);
    assertThat(authorize.statusCode()).isEqualTo(400);
    assertThat(authorize.headers().firstValue("Location")).isEmpty();
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/oauth2/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Origin", "https://attacker.test")
            .POST(
                HttpRequest.BodyPublishers.ofString("client_id=know-web&grant_type=refresh_token"))
            .build();
    var response = http.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("invalid_request");
  }

  @Test
  void accessTokensKeepFullLifetimeAndRefreshWindowExpiresFromSignIn() {
    var first = signIn("expiry");
    var jwt = decoder.decode(first.accessToken());
    assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
        .isEqualTo(Duration.ofHours(24));
    var sid = UUID.fromString(jwt.getClaimAsString("sid"));
    jdbc.update(
        "update identity_sessions set refresh_expires_at=now()-interval '1 second' where id=?",
        sid);
    assertThatThrownBy(() -> service.refresh(first.refreshToken(), "know-firefox"))
        .isInstanceOf(OAuthFailure.class);
    assertThat(service.active(sid, UUID.fromString(jwt.getSubject()))).isFalse();
  }
}
