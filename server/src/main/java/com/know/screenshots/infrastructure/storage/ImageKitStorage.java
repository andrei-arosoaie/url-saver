package com.know.screenshots.infrastructure.storage;

import com.know.screenshots.application.ScreenshotStorage;
import io.imagekit.client.ImageKitClient;
import io.imagekit.errors.NotFoundException;
import io.imagekit.models.SrcOptions;
import io.imagekit.models.assets.AssetListParams;
import io.imagekit.models.files.FileUploadParams;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ImageKitStorage implements ScreenshotStorage {
  private final ImageKitClient client;
  private final String endpoint;
  private final Clock clock;

  @Override
  public String provider() {
    return "imagekit";
  }

  @Override
  public StoredFile planned(UUID account, UUID upload, String contentType) {
    return new StoredFile(
        provider(),
        null,
        "/know/screenshots/"
            + account
            + "/"
            + upload
            + ("image/png".equals(contentType) ? ".png" : ".jpg"));
  }

  @Override
  public StoredFile upload(UUID accountId, UUID captureId, ImageFile image) {
    var extension = "image/png".equals(image.contentType()) ? ".png" : ".jpg";
    var result =
        client
            .files()
            .upload(
                FileUploadParams.builder()
                    .file(image.bytes())
                    .fileName(captureId + extension)
                    .folder("/know/screenshots/" + accountId)
                    .isPrivateFile(true)
                    .useUniqueFileName(false)
                    .overwriteFile(false)
                    .build());
    return new StoredFile(
        provider(), result.fileId().orElseThrow(), result.filePath().orElseThrow());
  }

  @Override
  public Download sign(StoredFile file, Duration lifetime) {
    requireProvider(file);
    var url =
        client
            .helper()
            .buildUrl(
                SrcOptions.builder()
                    .urlEndpoint(endpoint)
                    .src(file.path())
                    .signed(true)
                    .expiresIn((double) lifetime.toSeconds())
                    .build());
    return new Download(url, clock.instant().plus(lifetime));
  }

  @Override
  public void delete(StoredFile file) {
    requireProvider(file);
    if (file.id() != null) {
      deleteId(file.id());
      return;
    }
    int slash = file.path().lastIndexOf('/');
    String folder = file.path().substring(0, slash);
    String name = file.path().substring(slash + 1);
    // This path was journaled before uploading. Reconcile files whose upload
    // response was lost without ever listing another account's folder.
    for (var asset :
        client
            .assets()
            .list(
                AssetListParams.builder()
                    .path(folder)
                    .searchQuery("name=\"" + name + "\"")
                    .limit(100L)
                    .build())) {
      if (asset.isFile() && file.path().equals(asset.asFile().filePath().orElse(null)))
        asset.asFile().fileId().ifPresent(this::deleteId);
    }
  }

  private void deleteId(String id) {
    try {
      client.files().delete(id);
    } catch (NotFoundException alreadyRemoved) {
      /* Idempotent cleanup. */
    }
  }

  private void requireProvider(StoredFile file) {
    if (!provider().equals(file.provider()))
      throw new IllegalArgumentException("Storage provider mismatch.");
  }
}
