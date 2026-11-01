package com.know.identity.infrastructure;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "know.identity.enabled", havingValue = "true")
@RequestMapping("/api/v1/identity/profile")
public class IdentityProfileController {
  private final IdentityService service;

  @GetMapping
  public ResponseEntity<IdentityService.Profile> profile(Authentication authentication) {
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .body(service.profile((UUID) authentication.getPrincipal()));
  }

  @DeleteMapping("/picture")
  public ResponseEntity<IdentityService.Profile> removePicture(Authentication authentication) {
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .body(service.removePicture((UUID) authentication.getPrincipal()));
  }
}
