package com.know.identity.infrastructure;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class IdentitySecurity {
  @Bean
  @ConditionalOnProperty(
      name = "know.identity.enabled",
      havingValue = "false",
      matchIfMissing = true)
  SecurityFilterChain fixtureSecurity(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(a -> a.anyRequest().permitAll())
        .csrf(c -> c.disable())
        .build();
  }

  @Configuration
  @ConditionalOnProperty(name = "know.identity.enabled", havingValue = "true")
  static class Enabled {
    @Bean
    RSAKey signingKey(IdentityProperties p, org.springframework.core.env.Environment environment)
        throws Exception {
      if (p.signing() == null
          || p.cookie() == null
          || p.legacyAccount() == null
          || p.accessTokenLifetime() == null
          || p.refreshWindow() == null
          || p.accessTokenLifetime().isNegative()
          || p.accessTokenLifetime().isZero()
          || p.refreshWindow().isNegative()
          || p.refreshWindow().isZero()
          || !List.of("from-sign-in", "sliding").contains(p.refreshExpiryMode())
          || !"Lax".equals(p.cookie().sameSite())
          || p.legacyAccount().ownerEmail() == null
          || !p.legacyAccount().ownerEmail().contains("@")
          || p.legacyAccount().ownerEmail().contains("CHANGE_ME"))
        throw new IllegalStateException(
            "Complete the Know identity configuration and legacy owner email.");
      for (String key : List.of("client-id", "client-secret")) {
        var value =
            environment.getProperty("spring.security.oauth2.client.registration.google." + key, "");
        if (value.isBlank() || value.contains("CHANGE_ME"))
          throw new IllegalStateException(
              "Fill local Google OAuth credentials before enabling identity.");
      }
      var issuer = URI.create(p.issuer());
      if (!"https".equals(issuer.getScheme())
          && !("http".equals(issuer.getScheme()) && "localhost".equals(issuer.getHost())))
        throw new IllegalStateException(
            "Identity issuer must use HTTPS (localhost HTTP is allowed).");
      if ("https".equals(issuer.getScheme()) && !p.cookie().secure())
        throw new IllegalStateException("HTTPS requires Secure cookies.");
      if (!p.webRedirectUri().startsWith(p.webOrigin() + "/")
          || p.extensionRedirectUri().contains("GENERATED")
          || !p.extensionRedirectUri().startsWith("https://")
          || !p.extensionRedirectUri().contains(".extensions.allizom.org/"))
        throw new IllegalStateException("Configure exact web and Firefox redirect URIs.");
      var factory = KeyFactory.getInstance("RSA");
      var privateKey =
          (RSAPrivateKey)
              factory.generatePrivate(new PKCS8EncodedKeySpec(pem(p.signing().privateKey())));
      var publicKey =
          (RSAPublicKey)
              factory.generatePublic(new X509EncodedKeySpec(pem(p.signing().publicKey())));
      if (!privateKey.getModulus().equals(publicKey.getModulus())
          || publicKey.getModulus().bitLength() < 3072)
        throw new IllegalStateException("Matching RSA keys of at least 3072 bits are required.");
      return new RSAKey.Builder(publicKey)
          .privateKey(privateKey)
          .keyID(p.signing().keyId())
          .build();
    }

    private byte[] pem(org.springframework.core.io.Resource resource) throws IOException {
      var text = resource.getContentAsString(java.nio.charset.StandardCharsets.US_ASCII);
      return Base64.getDecoder()
          .decode(text.replaceAll("-----[^-]+-----", "").replaceAll("\\s", ""));
    }

    @Bean
    JwtEncoder encoder(RSAKey key) {
      return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    @Bean
    JwtDecoder decoder(RSAKey key, IdentityProperties properties) throws Exception {
      var decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
      decoder.setJwtValidator(
          new DelegatingOAuth2TokenValidator<>(
              JwtValidators.createDefaultWithIssuer(properties.issuer()),
              jwt ->
                  jwt.getAudience().contains("know-api")
                      ? OAuth2TokenValidatorResult.success()
                      : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
      return decoder;
    }

    @Bean
    @Order(1)
    SecurityFilterChain api(HttpSecurity http, JwtDecoder decoder, IdentityService service)
        throws Exception {
      var bearer =
          new OncePerRequestFilter() {
            protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
              String header = request.getHeader("Authorization");
              if (header != null) {
                try {
                  if (!header.startsWith("Bearer ")) throw new IllegalArgumentException();
                  var jwt = decoder.decode(header.substring(7));
                  var account = UUID.fromString(jwt.getSubject());
                  var session = UUID.fromString(jwt.getClaimAsString("sid"));
                  if (!service.active(session, account)) throw new IllegalArgumentException();
                  var auth =
                      UsernamePasswordAuthenticationToken.authenticated(account, null, List.of());
                  auth.setDetails(session);
                  SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (JwtException | IllegalArgumentException e) {
                  response.setStatus(401);
                  response.setHeader("WWW-Authenticate", "Bearer");
                  return;
                }
              }
              chain.doFilter(request, response);
            }
          };
      return http.securityMatcher("/api/**")
          .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          .securityContext(s -> s.securityContextRepository(new NullSecurityContextRepository()))
          .csrf(c -> c.disable())
          .addFilterBefore(bearer, AnonymousAuthenticationFilter.class)
          .authorizeHttpRequests(a -> a.anyRequest().authenticated())
          .exceptionHandling(
              e ->
                  e.authenticationEntryPoint(
                      (r, s, x) -> {
                        s.setStatus(401);
                        s.setHeader("WWW-Authenticate", "Bearer");
                      }))
          .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain login(
        HttpSecurity http, IdentityService service, IdentityProperties properties)
        throws Exception {
      return http.authorizeHttpRequests(a -> a.anyRequest().permitAll())
          .csrf(c -> c.ignoringRequestMatchers("/oauth2/token", "/oauth2/logout"))
          .requestCache(c -> c.disable())
          .oauth2Login(
              o ->
                  o.successHandler(
                          (request, response, authentication) -> {
                            var session = request.getSession(false);
                            var pending =
                                session == null
                                    ? null
                                    : (IdentityController.Pending)
                                        session.getAttribute("know.authorization");
                            try {
                              if (pending == null
                                  || pending.createdAt() + 300_000 < System.currentTimeMillis())
                                throw new OAuthFailure("invalid_request");
                              var user = (OidcUser) authentication.getPrincipal();
                              var account =
                                  service.resolveGoogle(
                                      user.getIssuer().toString(),
                                      user.getSubject(),
                                      user.getEmail(),
                                      Boolean.TRUE.equals(user.getEmailVerified()));
                              var code =
                                  service.authorize(
                                      account,
                                      pending.client(),
                                      pending.redirect(),
                                      pending.challenge());
                              session.invalidate();
                              SecurityContextHolder.clearContext();
                              response.setHeader("Cache-Control", "no-store");
                              response.sendRedirect(
                                  org.springframework.web.util.UriComponentsBuilder.fromUriString(
                                          pending.redirect())
                                      .queryParam("code", code)
                                      .queryParam("state", pending.state())
                                      .build()
                                      .encode()
                                      .toUriString());
                            } catch (OAuthFailure e) {
                              loginFailed(request, response, properties);
                            }
                          })
                      .failureHandler(
                          (request, response, error) -> {
                            loginFailed(request, response, properties);
                          }))
          .build();
    }

    private void loginFailed(
        HttpServletRequest request, HttpServletResponse response, IdentityProperties properties)
        throws IOException {
      var session = request.getSession(false);
      var pending =
          session == null
              ? null
              : (IdentityController.Pending) session.getAttribute("know.authorization");
      if (session != null) session.invalidate();
      SecurityContextHolder.clearContext();
      response.setHeader("Cache-Control", "no-store");
      if (pending != null
          && pending.createdAt() + 300_000 >= System.currentTimeMillis()
          && properties.validClient(pending.client(), pending.redirect())) {
        response.sendRedirect(
            org.springframework.web.util.UriComponentsBuilder.fromUriString(pending.redirect())
                .queryParam("error", "access_denied")
                .queryParam("state", pending.state())
                .build()
                .encode()
                .toUriString());
      } else response.sendRedirect(properties.webOrigin() + "/?auth_error=sign_in_failed");
    }
  }
}
