package com.know.screenshots.infrastructure.persistence;

import com.know.screenshots.application.ScreenshotStorage.*;
import com.know.screenshots.application.ScreenshotStore;
import com.know.shared.application.RequestFailure;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class JpaScreenshotStore implements ScreenshotStore {
  private final ScreenshotRepository screenshots;
  private final CleanupRepository cleanup;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  @org.springframework.context.event.EventListener
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public void articleDeleting(com.know.shared.application.ArticleDeleting event) {
    screenshots
        .lock(event.accountId(), event.articleId())
        .ifPresent(
            row -> {
              if (row.getFileId() != null)
                enqueue(
                    UUID.randomUUID(),
                    new StoredFile(row.getProvider(), row.getFileId(), row.getFilePath()),
                    Instant.now(),
                    Instant.now());
              screenshots.delete(row);
              screenshots.flush();
            });
    // An upload's separate pre-upload journal survives article deletion and cleans late files.
  }

  private State state(ScreenshotEntity entity) {
    if (entity == null) return new State(0, null, null, null, null, null, null);
    var file =
        entity.getFileId() == null
            ? null
            : new StoredFile(entity.getProvider(), entity.getFileId(), entity.getFilePath());
    return new State(
        entity.getVersion(),
        entity.getCaptureId(),
        file,
        entity.getContentType(),
        entity.getByteSize(),
        entity.getWidth(),
        entity.getHeight());
  }

  @Transactional(readOnly = true)
  public State read(UUID account, UUID article) {
    return state(screenshots.findByAccountIdAndArticleId(account, article).orElse(null));
  }

  @Transactional
  public Claim claim(
      UUID account,
      UUID article,
      UUID captureId,
      long expectedVersion,
      String hash,
      String provider,
      Instant now) {
    if (jdbc.query(
            "select id from articles where account_id=? and id=? for update",
            (r, n) -> r.getObject(1, UUID.class),
            account,
            article)
        .isEmpty()) throw new RequestFailure(404, "RESOURCE_NOT_FOUND", "Article not found.");
    screenshots.ensure(account, article);
    var row = screenshots.lock(account, article).orElseThrow();
    boolean same = captureId.equals(row.getCaptureId());
    if (same && !hash.equals(row.getSourceHash()))
      throw conflict("Capture ID was reused with different image bytes.");
    if (same && row.getFileId() != null) return new Claim(state(row), null);
    if (!same && row.getVersion() != expectedVersion)
      throw conflict("Screenshot changed; reload before replacing it.");
    if (row.getLeaseId() != null && row.getLeaseUntil().isAfter(now))
      throw new RequestFailure(
          503, "SCREENSHOT_UPLOAD_BUSY", "Screenshot upload is already running. Retry later.");
    if (!same) {
      if (row.getFileId() != null)
        enqueue(
            UUID.randomUUID(),
            new StoredFile(row.getProvider(), row.getFileId(), row.getFilePath()),
            now,
            now);
      row.setVersion(row.getVersion() + 1);
      row.setCaptureId(captureId);
      row.setSourceHash(hash);
      row.setFileId(null);
      row.setFilePath(null);
      row.setContentType(null);
      row.setByteSize(null);
      row.setWidth(null);
      row.setHeight(null);
    }
    var lease = UUID.randomUUID();
    row.setLeaseId(lease);
    row.setLeaseUntil(now.plusSeconds(120));
    row.setProvider(provider);
    screenshots.flush();
    return new Claim(state(row), lease);
  }

  private void enqueue(UUID id, StoredFile file, Instant next, Instant retain) {
    var row = new CleanupEntity();
    row.setId(id);
    row.setProvider(file.provider());
    row.setFileId(file.id());
    row.setFilePath(file.path());
    row.setNextAttemptAt(next);
    row.setRetainUntil(retain);
    cleanup.saveAndFlush(row);
  }

  @Transactional
  public void journal(UUID lease, StoredFile file, Instant now) {
    // Written BEFORE the remote upload, so even a lost provider response has a known cleanup path.
    enqueue(lease, file, now.plusSeconds(300), now.plusSeconds(86400));
  }

  @Transactional
  public boolean complete(
      UUID account, UUID article, UUID capture, UUID lease, StoredFile file, ImageFile image) {
    var row = screenshots.lock(account, article).orElse(null);
    if (row == null || !capture.equals(row.getCaptureId()) || !lease.equals(row.getLeaseId()))
      return false;
    row.setLeaseId(null);
    row.setLeaseUntil(null);
    if (file != null) {
      row.setProvider(file.provider());
      row.setFileId(file.id());
      row.setFilePath(file.path());
      row.setContentType(image.contentType());
      row.setByteSize((long) image.bytes().length);
      row.setWidth(image.width());
      row.setHeight(image.height());
      cleanup.deleteById(lease);
    }
    screenshots.flush();
    return true;
  }

  @Transactional
  public void failed(UUID account, UUID article, UUID lease) {
    var row = screenshots.lock(account, article).orElse(null);
    if (row != null && lease.equals(row.getLeaseId())) {
      row.setLeaseId(null);
      row.setLeaseUntil(null);
    }
  }

  @Transactional(readOnly = true)
  public List<Cleanup> due(Instant now) {
    return cleanup.findTop20ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(now).stream()
        .map(
            c ->
                new Cleanup(
                    c.getId(),
                    new StoredFile(c.getProvider(), c.getFileId(), c.getFilePath()),
                    c.getRetainUntil(),
                    c.getAttempts()))
        .toList();
  }

  @Transactional
  public void cleaned(UUID id) {
    cleanup.deleteById(id);
  }

  @Transactional
  public void postpone(Cleanup item, Instant now) {
    cleanup
        .findById(item.id())
        .ifPresent(
            row -> {
              row.setAttempts(row.getAttempts() + 1);
              row.setNextAttemptAt(
                  now.plusSeconds(Math.min(3600, 300L << Math.min(4, row.getAttempts()))));
            });
  }

  private static RequestFailure conflict(String message) {
    return new RequestFailure(409, "SCREENSHOT_VERSION_CONFLICT", message);
  }
}
