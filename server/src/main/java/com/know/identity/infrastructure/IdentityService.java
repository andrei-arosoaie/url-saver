package com.know.identity.infrastructure;

import com.know.accounts.application.AccountRegistration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "know.identity.enabled", havingValue = "true")
public class IdentityService {
  private final JdbcTemplate jdbc;
  private final AccountRegistration accounts;
  private final IdentityProperties properties;
  private final JwtEncoder encoder;
  private static final SecureRandom RANDOM = new SecureRandom();

  public record Tokens(String accessToken, String refreshToken, long expiresIn) {}

  public record Profile(String displayName, String pictureUrl, boolean pictureRemoved) {}

  private record Session(UUID id, UUID account, String client, Instant expiry, boolean revoked) {}

  public static String randomToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public static String hash(String value) {
    try {
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(
              MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(StandardCharsets.US_ASCII)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @Transactional
  public UUID resolveGoogle(String issuer, String subject, String email, boolean verified) {
    return resolveGoogle(issuer, subject, email, verified, null, null);
  }

  @Transactional
  public UUID resolveGoogle(
      String issuer, String subject, String email, boolean verified, String name, String picture) {
    if (!List.of("https://accounts.google.com", "accounts.google.com").contains(issuer)
        || subject == null
        || subject.isBlank()
        || !verified) throw new OAuthFailure("access_denied");
    // Serialize first sign-ins and the one-time legacy claim across server instances.
    jdbc.queryForObject("select pg_advisory_xact_lock(912009)", Object.class);
    var existing =
        jdbc.query(
            "select account_id from external_account_providers where issuer=? and subject=?",
            (rs, n) -> rs.getObject(1, UUID.class),
            "https://accounts.google.com",
            subject);
    if (!existing.isEmpty()) {
      updateProfile(existing.getFirst(), name, email, picture);
      return existing.getFirst();
    }
    UUID account;
    var legacy = properties.legacyAccount();
    boolean owner = email != null && email.equalsIgnoreCase(legacy.ownerEmail());
    boolean available =
        Boolean.TRUE.equals(
            jdbc.queryForObject(
                "select exists(select 1 from accounts where id=?) and not exists(select 1 from external_account_providers where account_id=?)",
                Boolean.class,
                legacy.id(),
                legacy.id()));
    if (owner && available) account = legacy.id();
    else account = accounts.create();
    jdbc.update(
        "insert into external_account_providers(issuer,subject,account_id) values (?,?,?)",
        "https://accounts.google.com",
        subject,
        account);
    updateProfile(account, name, email, picture);
    return account;
  }

  private void updateProfile(UUID account, String name, String email, String picture) {
    String displayName =
        name == null || name.isBlank() ? email == null ? "Your account" : email : name;
    String safePicture = null;
    if (picture != null && picture.length() <= 2048) {
      try {
        var uri = java.net.URI.create(picture);
        if ("https".equals(uri.getScheme())
            && uri.getUserInfo() == null
            && (uri.getPort() == -1 || uri.getPort() == 443)
            && uri.getHost() != null
            && (uri.getHost().equals("googleusercontent.com")
                || uri.getHost().endsWith(".googleusercontent.com"))) safePicture = picture;
      } catch (IllegalArgumentException ignored) {
        /* A missing photo never blocks sign-in. */
      }
    }
    jdbc.update(
        "insert into identity_profiles(account_id,display_name,picture_url) values (?,?,?) on conflict(account_id) do update set display_name=excluded.display_name,picture_url=case when identity_profiles.picture_removed then null else excluded.picture_url end",
        account,
        displayName,
        safePicture);
  }

  public Profile profile(UUID account) {
    return jdbc
        .query(
            "select display_name,picture_url,picture_removed from identity_profiles where account_id=?",
            (rs, n) -> new Profile(rs.getString(1), rs.getString(2), rs.getBoolean(3)),
            account)
        .stream()
        .findFirst()
        .orElse(new Profile("Your account", null, false));
  }

  @Transactional
  public Profile removePicture(UUID account) {
    jdbc.update(
        "insert into identity_profiles(account_id,display_name,picture_removed) values (?,'Your account',true) on conflict(account_id) do update set picture_url=null,picture_removed=true",
        account);
    return profile(account);
  }

  @Transactional
  public String authorize(UUID account, String client, String redirect, String challenge) {
    if (!properties.validClient(client, redirect) || !challenge.matches("[A-Za-z0-9_-]{43}"))
      throw new OAuthFailure("invalid_request");
    var code = randomToken();
    jdbc.update(
        "insert into identity_codes values (?,?,?,?,?,?)",
        hash(code),
        account,
        client,
        redirect,
        challenge,
        Timestamp.from(Instant.now().plusSeconds(120)));
    return code;
  }

  @Transactional(noRollbackFor = OAuthFailure.class)
  public Tokens exchange(String code, String client, String redirect, String verifier) {
    if (code == null
        || verifier == null
        || !verifier.matches("[A-Za-z0-9._~-]{43,128}")
        || !properties.validClient(client, redirect)) throw new OAuthFailure("invalid_grant");
    var rows =
        jdbc.query(
            "select * from identity_codes where code_hash=? for update",
            (rs, n) -> {
              if (!client.equals(rs.getString("client_id"))
                  || !redirect.equals(rs.getString("redirect_uri"))
                  || !hash(verifier).equals(rs.getString("challenge"))
                  || !rs.getTimestamp("expires_at").toInstant().isAfter(Instant.now()))
                throw new OAuthFailure("invalid_grant");
              return rs.getObject("account_id", UUID.class);
            },
            hash(code));
    if (rows.isEmpty()) throw new OAuthFailure("invalid_grant");
    jdbc.update("delete from identity_codes where code_hash=?", hash(code));
    var now = Instant.now();
    var session =
        new Session(
            UUID.randomUUID(),
            rows.getFirst(),
            client,
            now.plus(properties.refreshWindow()),
            false);
    jdbc.update(
        "insert into identity_sessions(id,account_id,client_id,signed_in_at,refresh_expires_at) values (?,?,?,?,?)",
        session.id(),
        session.account(),
        client,
        Timestamp.from(now),
        Timestamp.from(session.expiry()));
    return issue(session);
  }

  @Transactional(noRollbackFor = OAuthFailure.class)
  public Tokens refresh(String token, String client) {
    if (token == null || token.length() > 256) throw new OAuthFailure("invalid_grant");
    // Lock the session before examining token state, so rotation/reuse has one ordering.
    var sessions =
        jdbc.query(
            "select s.* from identity_sessions s join identity_refresh_tokens t on t.session_id=s.id where t.token_hash=? for update of s",
            (rs, n) ->
                new Session(
                    rs.getObject("id", UUID.class),
                    rs.getObject("account_id", UUID.class),
                    rs.getString("client_id"),
                    rs.getTimestamp("refresh_expires_at").toInstant(),
                    rs.getBoolean("revoked")),
            hash(token));
    if (sessions.isEmpty()) throw new OAuthFailure("invalid_grant");
    var session = sessions.getFirst();
    if (!session.client().equals(client)) throw new OAuthFailure("invalid_grant");
    boolean used =
        Boolean.TRUE.equals(
            jdbc.queryForObject(
                "select used from identity_refresh_tokens where token_hash=?",
                Boolean.class,
                hash(token)));
    if (used || session.revoked() || !session.expiry().isAfter(Instant.now())) {
      revoke(session.id());
      throw new OAuthFailure("invalid_grant");
    }
    jdbc.update("update identity_refresh_tokens set used=true where token_hash=?", hash(token));
    if ("sliding".equals(properties.refreshExpiryMode())) {
      session =
          new Session(
              session.id(),
              session.account(),
              client,
              Instant.now().plus(properties.refreshWindow()),
              false);
      jdbc.update(
          "update identity_sessions set refresh_expires_at=? where id=?",
          Timestamp.from(session.expiry()),
          session.id());
    }
    return issue(session);
  }

  private Tokens issue(Session session) {
    var now = Instant.now();
    var refresh = randomToken();
    jdbc.update(
        "insert into identity_refresh_tokens(token_hash,session_id) values (?,?)",
        hash(refresh),
        session.id());
    var claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .subject(session.account().toString())
            .audience(List.of("know-api"))
            .issuedAt(now)
            .expiresAt(now.plus(properties.accessTokenLifetime()))
            .id(UUID.randomUUID().toString())
            .claim("sid", session.id().toString())
            .build();
    var header =
        JwsHeader.with(SignatureAlgorithm.RS256).keyId(properties.signing().keyId()).build();
    return new Tokens(
        encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(),
        refresh,
        properties.accessTokenLifetime().toSeconds());
  }

  public boolean active(UUID session, UUID account) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 from identity_sessions where id=? and account_id=? and not revoked)",
            Boolean.class,
            session,
            account));
  }

  public void revoke(UUID session) {
    jdbc.update("update identity_sessions set revoked=true where id=?", session);
  }

  @Transactional
  public void logoutRefresh(String token, String client) {
    if (token == null) return;
    jdbc.update(
        "update identity_sessions set revoked=true where client_id=? and id in (select session_id from identity_refresh_tokens where token_hash=?)",
        client,
        hash(token));
  }

  public void verifySessionBinding(String token, String client, String expectedSession) {
    if (expectedSession == null || token == null) return;
    var sessions =
        jdbc.query(
            "select s.id from identity_sessions s join identity_refresh_tokens t on t.session_id=s.id where t.token_hash=? and s.client_id=?",
            (rs, n) -> rs.getObject(1, UUID.class).toString(),
            hash(token),
            client);
    if (!sessions.isEmpty() && !sessions.getFirst().equals(expectedSession))
      throw new OAuthFailure("invalid_grant");
  }
}
