package com.know.screenshots.infrastructure.imaging;

import static org.assertj.core.api.Assertions.*;

import com.know.screenshots.application.ScreenshotPolicy;
import com.know.shared.application.RequestFailure;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class NativePngTransformationTest {
  private NativePngTransformation transform(
      String format, int quality, long input, long output, long pixels) {
    return new NativePngTransformation(
        new ScreenshotPolicy(format, quality, input, output, pixels));
  }

  @Test
  void preservesNativeDimensionsWhileTheBackendSelectsJpegQualityOrLosslessPng() throws Exception {
    var image = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < image.getHeight(); y++)
      for (int x = 0; x < image.getWidth(); x++)
        image.setRGB(x, y, (x * 17317 + y * 39217) & 0xffffff);
    var output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    var nativePng = output.toByteArray();
    var jpeg = transform("jpeg", 90, 1000000, 1000000, 1000000).transform(nativePng);
    assertThat(jpeg.contentType()).isEqualTo("image/jpeg");
    assertThat(jpeg.width()).isEqualTo(320);
    assertThat(jpeg.height()).isEqualTo(180);
    var decoded = ImageIO.read(new ByteArrayInputStream(jpeg.bytes()));
    assertThat(decoded.getWidth()).isEqualTo(320);
    assertThat(decoded.getHeight()).isEqualTo(180);
    var lowerQuality = transform("jpeg", 30, 1000000, 1000000, 1000000).transform(nativePng);
    assertThat(lowerQuality.bytes().length).isLessThan(jpeg.bytes().length);
    var png = transform("png", 90, 1000000, 1000000, 1000000).transform(nativePng);
    assertThat(png.contentType()).isEqualTo("image/png");
    var lossless = ImageIO.read(new ByteArrayInputStream(png.bytes()));
    assertThat(lossless.getRGB(150, 80)).isEqualTo(image.getRGB(150, 80));
  }

  @Test
  void rejectsInvalidInputAndEnforcesSeparateInputOutputAndDecodedMemoryLimits() throws Exception {
    var image = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(image, "png", bytes);
    var png = bytes.toByteArray();
    assertThatThrownBy(() -> transform("jpeg", 90, 1, 100000, 100000).transform(png))
        .isInstanceOfSatisfying(
            RequestFailure.class, f -> assertThat(f.getStatus()).isEqualTo(413));
    assertThatThrownBy(() -> transform("jpeg", 90, 100000, 1, 100000).transform(png))
        .isInstanceOfSatisfying(
            RequestFailure.class, f -> assertThat(f.getStatus()).isEqualTo(413));
    assertThatThrownBy(() -> transform("jpeg", 90, 100000, 100000, 10).transform(png))
        .isInstanceOfSatisfying(
            RequestFailure.class,
            f -> assertThat(f.getReason()).isEqualTo("SCREENSHOT_DIMENSIONS_TOO_LARGE"));
    assertThatThrownBy(
            () -> transform("jpeg", 90, 100000, 100000, 100000).transform(new byte[] {1, 2, 3}))
        .isInstanceOfSatisfying(
            RequestFailure.class, f -> assertThat(f.getStatus()).isEqualTo(400));
  }
}
