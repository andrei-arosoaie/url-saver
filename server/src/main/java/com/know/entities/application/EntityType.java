package com.know.entities.application;

import com.know.shared.application.RequestFailure;
import java.util.Set;

public enum EntityType {
  BOOK("TO_READ", "READ", Set.of("TO_READ", "READING", "READ")),
  MOVIE("TO_BE_SEEN", "SEEN", Set.of("TO_BE_SEEN", "SEEN")),
  TV_SHOW("TO_BE_SEEN", "SEEN", Set.of("TO_BE_SEEN", "SEEING", "SEEN")),
  GAME("TO_PLAY", "FINISHED", Set.of("TO_PLAY", "PLAYING", "FINISHED"));

  public final String initialStatus;
  public final String completedStatus;
  private final Set<String> statuses;

  EntityType(String initial, String completed, Set<String> statuses) {
    initialStatus = initial;
    completedStatus = completed;
    this.statuses = statuses;
  }

  public void validateStatus(String status) {
    if (!statuses.contains(status == null ? "" : status))
      throw new RequestFailure(
          400, "INVALID_ENTITY_STATUS", "Status does not belong to " + name() + ".");
  }
}
