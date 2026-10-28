package com.know.screenshots.infrastructure.imaging;

import com.know.screenshots.application.ImageTransformation;
import com.know.screenshots.application.ScreenshotPolicy;
import com.know.screenshots.application.ScreenshotStorage.ImageFile;
import com.know.shared.application.RequestFailure;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class NativePngTransformation implements ImageTransformation {
  private final ScreenshotPolicy policy;
  // Bound decoded-image memory on the small runtime; do not build an unbounded queue.
  private final Semaphore processing = new Semaphore(1);

  @Override
  public ImageFile transform(byte[] bytes) {
    if (bytes == null || bytes.length == 0) throw invalid();
    if (bytes.length > policy.maxUploadBytes())
      throw new RequestFailure(
          413, "SCREENSHOT_TOO_LARGE", "Screenshot upload exceeds the configured limit.");
    if (!processing.tryAcquire())
      throw new RequestFailure(
          503,
          "SCREENSHOT_PROCESSOR_BUSY",
          "Screenshot processor is busy. Retry the image upload.");
    try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw invalid();
      var reader = readers.next();
      try {
        if (!reader.getFormatName().equalsIgnoreCase("png")) throw invalid();
        reader.setInput(input, true, true);
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        if (width < 1 || height < 1 || (long) width * height > policy.maxDecodedPixels())
          throw new RequestFailure(
              413,
              "SCREENSHOT_DIMENSIONS_TOO_LARGE",
              "Screenshot exceeds the configured decoded-pixel limit.");
        var decoded = reader.read(0);
        try {
          var encoded = "png".equals(policy.format()) ? png(decoded) : jpeg(decoded);
          if (encoded.length > policy.maxStoredBytes())
            throw new RequestFailure(
                413,
                "SCREENSHOT_TOO_LARGE",
                "Transformed screenshot exceeds the configured stored-image limit.");
          return new ImageFile(encoded, "image/" + policy.format(), width, height);
        } finally {
          decoded.flush();
        }
      } finally {
        reader.dispose();
      }
    } catch (IOException | IllegalArgumentException error) {
      throw invalid();
    } finally {
      processing.release();
    }
  }

  private byte[] png(BufferedImage image) throws IOException {
    var output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    return output.toByteArray();
  }

  private byte[] jpeg(BufferedImage image) throws IOException {
    var rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
    var graphics = rgb.createGraphics();
    try {
      graphics.setColor(Color.WHITE);
      graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
      graphics.drawImage(image, 0, 0, null);
    } finally {
      graphics.dispose();
    }
    var bytes = new ByteArrayOutputStream();
    var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    try (var output = new MemoryCacheImageOutputStream(bytes)) {
      writer.setOutput(output);
      var parameters = writer.getDefaultWriteParam();
      parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      parameters.setCompressionQuality(policy.jpegQuality() / 100f);
      writer.write(null, new IIOImage(rgb, null, null), parameters);
      output.flush();
      return bytes.toByteArray();
    } finally {
      writer.dispose();
      rgb.flush();
    }
  }

  private static RequestFailure invalid() {
    return new RequestFailure(
        400, "INVALID_SCREENSHOT", "Supply a valid lossless PNG viewport capture.");
  }
}
