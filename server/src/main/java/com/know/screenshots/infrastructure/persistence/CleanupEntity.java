package com.know.screenshots.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "screenshot_cleanup")
@Getter
@Setter
@NoArgsConstructor
public class CleanupEntity {
  @Id private UUID id;
  private String provider;
  private String fileId;
  private String filePath;
  private Instant nextAttemptAt;
  private Instant retainUntil;
  private int attempts;
}
