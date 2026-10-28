package com.know.screenshots.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "article_screenshots")
@Getter
@Setter
@NoArgsConstructor
public class ScreenshotEntity {
  @Id private UUID articleId;
  private UUID accountId;
  private long version;
  private UUID captureId;
  private String sourceHash;
  private UUID leaseId;
  private Instant leaseUntil;
  private String provider;
  private String fileId;
  private String filePath;
  private String contentType;
  private Long byteSize;
  private Integer width;
  private Integer height;
}
