package com.know.articles.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class UrlHasher {
  public byte[] sha256(String exactUrl) {
    Objects.requireNonNull(exactUrl);
    try {
      return MessageDigest.getInstance("SHA-256").digest(exactUrl.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Required SHA-256 is unavailable", exception);
    }
  }
}
