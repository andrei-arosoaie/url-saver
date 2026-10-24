package com.know.accounts.infrastructure.persistence;

import com.know.accounts.application.TemporaryAccount;
import com.know.accounts.infrastructure.config.TemporaryAccountProperties;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ConfiguredTemporaryAccount implements TemporaryAccount {
  private final TemporaryAccountProperties properties;
  private final JdbcTemplate jdbc;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<UUID> resolve() {
    if (!properties.enabled()) return Optional.empty();
    jdbc.update(
        "insert into accounts(id,created_at) values (?,current_timestamp) on conflict (id) do nothing",
        properties.id());
    return Optional.of(properties.id());
  }
}
