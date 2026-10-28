package com.know.screenshots.application;

public interface ImageTransformation {
  ScreenshotStorage.ImageFile transform(byte[] nativePng);
}
