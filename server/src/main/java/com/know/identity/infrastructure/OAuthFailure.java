package com.know.identity.infrastructure;

public class OAuthFailure extends RuntimeException {
  public OAuthFailure(String error) {
    super(error);
  }
}
