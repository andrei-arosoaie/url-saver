package com.know.accounts.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "accounts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountEntity {
  @Id private UUID id;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
