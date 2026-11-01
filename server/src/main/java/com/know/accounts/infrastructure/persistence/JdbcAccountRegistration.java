package com.know.accounts.infrastructure.persistence;

import com.know.accounts.application.AccountRegistration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JdbcAccountRegistration implements AccountRegistration {
  private final JdbcTemplate jdbc;

  @Transactional
  public UUID create() {
    var id = UUID.randomUUID();
    jdbc.update("insert into accounts(id,created_at) values (?,current_timestamp)", id);
    return id;
  }
}
