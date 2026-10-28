package com.know.screenshots.application;

/** Limits are supplied by server configuration; clients do not choose transformations. */
public record ScreenshotPolicy(
    String format,
    int jpegQuality,
    long maxUploadBytes,
    long maxStoredBytes,
    long maxDecodedPixels) {
  public ScreenshotPolicy {
    if (!("jpeg".equals(format) || "png".equals(format))
        || jpegQuality < 1
        || jpegQuality > 100
        || maxUploadBytes < 1
        || maxStoredBytes < 1
        || maxDecodedPixels < 1) {
      throw new IllegalArgumentException("Invalid screenshot transformation configuration.");
    }
  }
}
