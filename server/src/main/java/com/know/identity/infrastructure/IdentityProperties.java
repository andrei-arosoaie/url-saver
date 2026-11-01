package com.know.identity.infrastructure;

import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties("know.identity")
public record IdentityProperties(
    boolean enabled,
    String issuer,
    String webOrigin,
    String extensionId,
    String extensionClientId,
    String extensionRedirectUri,
    String webRedirectUri,
    Signing signing,
    Duration accessTokenLifetime,
    Duration refreshWindow,
    String refreshExpiryMode,
    Cookie cookie,
    LegacyAccount legacyAccount) {
  public record Signing(String keyId, Resource privateKey, Resource publicKey) {}

  public record Cookie(boolean secure, String sameSite) {}

  public record LegacyAccount(UUID id, String ownerEmail) {}

  public boolean web(String client) {
    return "know-web".equals(client);
  }

  public boolean validClient(String client, String redirect) {
    return web(client)
        ? webRedirectUri.equals(redirect)
        : extensionClientId.equals(client) && extensionRedirectUri.equals(redirect);
  }
}
