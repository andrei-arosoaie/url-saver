package com.know.shared.application;

import java.util.UUID;
import lombok.Getter;

@Getter
public class RequestFailure extends RuntimeException {
  private final int status;
  private final String reason;
  private final UUID existingEntityId;

  public RequestFailure(int status, String reason, String message) {
    this(status, reason, message, null);
  }

  public RequestFailure(int status, String reason, String message, UUID existingEntityId) {
    super(message);
    this.status = status;
    this.reason = reason;
    this.existingEntityId = existingEntityId;
  }
}
