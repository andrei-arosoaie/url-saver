package com.know.accounts.application;

import java.util.Optional;
import java.util.UUID;

public interface TemporaryAccount {
  Optional<UUID> resolve();
}
